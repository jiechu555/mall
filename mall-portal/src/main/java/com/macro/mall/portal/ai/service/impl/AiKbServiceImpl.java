package com.macro.mall.portal.ai.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.macro.mall.mapper.PmsBrandMapper;
import com.macro.mall.mapper.PmsProductAttributeMapper;
import com.macro.mall.mapper.PmsProductAttributeValueMapper;
import com.macro.mall.mapper.PmsProductMapper;
import com.macro.mall.mapper.PmsSkuStockMapper;
import com.macro.mall.model.PmsBrand;
import com.macro.mall.model.PmsProduct;
import com.macro.mall.model.PmsProductAttribute;
import com.macro.mall.model.PmsProductAttributeValue;
import com.macro.mall.model.PmsProductAttributeValueExample;
import com.macro.mall.model.PmsProductExample;
import com.macro.mall.model.PmsSkuStock;
import com.macro.mall.model.PmsSkuStockExample;
import com.macro.mall.portal.ai.domain.AiKbHit;
import com.macro.mall.portal.ai.service.AiKbService;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseException;
import org.elasticsearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 知识库服务实现：统一走低层 RestClient（索引管理 / bulk / 检索一把抓，
 * 向量检索（script_score）后续 commit 也在同一客户端上扩展，不混两套 API）。
 * mapping 的 dense_vector(1024) 本 commit 即建好——维度与索引绑定，
 * 向量化管道接入后无需 reindex（写入时补字段即可）。
 * Created by jiechu555 on 2026/10/01.
 */
@Service
public class AiKbServiceImpl implements AiKbService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AiKbServiceImpl.class);

    public static final String INDEX = "ai_kb";

    private static final String MAPPING_JSON = "{"
            + "\"settings\":{\"number_of_shards\":1,\"number_of_replicas\":0},"
            + "\"mappings\":{\"properties\":{"
            + "\"source_type\":{\"type\":\"keyword\"},"
            + "\"source_id\":{\"type\":\"long\"},"
            + "\"title\":{\"type\":\"text\",\"analyzer\":\"ik_max_word\"},"
            + "\"content\":{\"type\":\"text\",\"analyzer\":\"ik_max_word\"},"
            + "\"content_vector\":{\"type\":\"dense_vector\",\"dims\":1024,\"doc_values\":true}"
            + "}}}";

    @Autowired
    private RestClient restClient;
    @Autowired
    private PmsProductMapper productMapper;
    @Autowired
    private PmsBrandMapper brandMapper;
    @Autowired
    private PmsProductAttributeMapper attributeMapper;
    @Autowired
    private PmsProductAttributeValueMapper attributeValueMapper;
    @Autowired
    private PmsSkuStockMapper skuStockMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public synchronized int rebuild() {
        try {
            deleteIndexIfExists();
            Request create = new Request("PUT", "/" + INDEX);
            create.setJsonEntity(MAPPING_JSON);
            restClient.performRequest(create);

            StringBuilder bulk = new StringBuilder();
            int count = 0;
            for (ObjectNode doc : buildProductDocs()) {
                appendBulkLine(bulk, doc);
                count++;
            }
            for (ObjectNode doc : buildFaqDocs()) {
                appendBulkLine(bulk, doc);
                count++;
            }
            if (count > 0) {
                Request bulkRequest = new Request("POST", "/_bulk");
                bulkRequest.setJsonEntity(bulk.toString());
                restClient.performRequest(bulkRequest);
                restClient.performRequest(new Request("POST", "/" + INDEX + "/_refresh"));
            }
            LOGGER.info("AI 知识库重建完成：{} 篇文档", count);
            return count;
        } catch (Exception e) {
            throw new RuntimeException("AI 知识库重建失败", e);
        }
    }

    @Override
    public long docCount() {
        try {
            Response response = restClient.performRequest(new Request("GET", "/" + INDEX + "/_count"));
            JsonNode root = objectMapper.readTree(EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8));
            return root.path("count").asLong();
        } catch (Exception e) {
            return -1;
        }
    }

    @Override
    public List<AiKbHit> searchByKeyword(String keyword, int topK) {
        try {
            ObjectNode body = objectMapper.createObjectNode();
            ObjectNode multiMatch = body.putObject("query").putObject("multi_match");
            multiMatch.put("query", keyword);
            multiMatch.put("type", "best_fields");
            // fields 必须是数组：传字符串时 ES 把整串当一个字段名，^2,content 会被当作 boost 解析而报 number_format
            multiMatch.putArray("fields").add("title^2").add("content");
            body.put("size", topK);
            body.putArray("_source").add("source_type").add("source_id").add("title").add("content");

            Request request = new Request("POST", "/" + INDEX + "/_search");
            request.setJsonEntity(objectMapper.writeValueAsString(body));
            Response response = restClient.performRequest(request);
            JsonNode hits = objectMapper.readTree(EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8))
                    .path("hits").path("hits");

            List<AiKbHit> result = new ArrayList<>();
            for (JsonNode hit : hits) {
                JsonNode src = hit.path("_source");
                AiKbHit item = new AiKbHit();
                item.setSourceType(src.path("source_type").asText());
                item.setSourceId(src.path("source_id").asLong());
                item.setTitle(src.path("title").asText());
                item.setScore(hit.path("_score").asDouble());
                String content = src.path("content").asText("");
                item.setSnippet(content.length() > 120 ? content.substring(0, 120) + "..." : content);
                result.add(item);
            }
            return result;
        } catch (Exception e) {
            LOGGER.error("BM25 检索失败: {}", keyword, e);
            return new ArrayList<>();
        }
    }

    /** 商品卡片：一商品一文档（结构化模板文本，天然短于 500 字无需分块） */
    private List<ObjectNode> buildProductDocs() {
        PmsProductExample example = new PmsProductExample();
        example.createCriteria().andPublishStatusEqualTo(1).andDeleteStatusEqualTo(0);
        List<PmsProduct> products = productMapper.selectByExample(example);

        List<ObjectNode> docs = new ArrayList<>();
        for (PmsProduct product : products) {
            StringBuilder content = new StringBuilder();
            content.append("商品名称：").append(product.getName()).append("。");
            if (product.getBrandId() != null) {
                PmsBrand brand = brandMapper.selectByPrimaryKey(product.getBrandId());
                if (brand != null) {
                    content.append("品牌：").append(brand.getName()).append("。");
                }
            }
            if (product.getPrice() != null) {
                content.append("价格：").append(product.getPrice()).append(" 元起。");
            }
            content.append("库存：").append(stockStatus(product.getId())).append("。");
            if (product.getDescription() != null && !product.getDescription().isEmpty()) {
                String desc = product.getDescription().replaceAll("<[^>]+>", "");
                content.append("简介：").append(desc, 0, Math.min(desc.length(), 80)).append("。");
            }
            content.append("主要参数：");
            int attrCount = 0;
            for (String attr : productAttributes(product.getId())) {
                if (attrCount >= 8) {
                    break;
                }
                content.append(attr).append("；");
                attrCount++;
            }
            content.append("。");

            docs.add(doc("PRODUCT", product.getId(), product.getName(), content.toString()));
        }
        return docs;
    }

    /** FAQ 种子：一条一文档（repo 内 JSON 维护，规则取自平台真实策略） */
    private List<ObjectNode> buildFaqDocs() {
        List<ObjectNode> docs = new ArrayList<>();
        try (InputStream in = new ClassPathResource("ai/faq-seeds.json").getInputStream()) {
            JsonNode faqs = objectMapper.readTree(in).path("faqs");
            for (JsonNode faq : faqs) {
                String title = faq.path("question").asText();
                String content = "【" + faq.path("category").asText() + "】"
                        + faq.path("answer").asText();
                docs.add(doc("FAQ", faq.path("id").asLong(), title, content));
            }
        } catch (Exception e) {
            LOGGER.error("FAQ 种子加载失败", e);
        }
        return docs;
    }

    private String stockStatus(Long productId) {
        PmsSkuStockExample example = new PmsSkuStockExample();
        example.createCriteria().andProductIdEqualTo(productId);
        List<PmsSkuStock> skus = skuStockMapper.selectByExample(example);
        long available = skus.stream().filter(s -> s.getStock() != null && s.getStock() > 0).count();
        if (available == 0) {
            return "暂无库存";
        }
        return "现货（" + available + "/" + skus.size() + " 个规格有货）";
    }

    private List<String> productAttributes(Long productId) {
        PmsProductAttributeValueExample example = new PmsProductAttributeValueExample();
        example.createCriteria().andProductIdEqualTo(productId);
        List<PmsProductAttributeValue> values = attributeValueMapper.selectByExample(example);
        List<String> attrs = new ArrayList<>();
        for (PmsProductAttributeValue value : values) {
            if (value.getProductAttributeId() == null) {
                continue;
            }
            PmsProductAttribute attribute = attributeMapper.selectByPrimaryKey(value.getProductAttributeId());
            if (attribute != null && value.getValue() != null) {
                attrs.add(attribute.getName() + "=" + value.getValue());
            }
        }
        return attrs;
    }

    private ObjectNode doc(String sourceType, Long sourceId, String title, String content) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("source_type", sourceType);
        node.put("source_id", sourceId);
        node.put("title", title);
        node.put("content", content);
        return node;
    }

    private void appendBulkLine(StringBuilder bulk, ObjectNode doc) throws Exception {
        ObjectNode action = objectMapper.createObjectNode();
        action.putObject("index").put("_index", INDEX);
        bulk.append(objectMapper.writeValueAsString(action)).append('\n');
        bulk.append(objectMapper.writeValueAsString(doc)).append('\n');
    }

    private void deleteIndexIfExists() throws Exception {
        try {
            restClient.performRequest(new Request("DELETE", "/" + INDEX));
        } catch (ResponseException e) {
            if (e.getResponse().getStatusLine().getStatusCode() != 404) {
                throw e;
            }
        }
    }
}
