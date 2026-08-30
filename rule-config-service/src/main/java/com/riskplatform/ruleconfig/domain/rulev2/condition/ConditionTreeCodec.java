package com.riskplatform.ruleconfig.domain.rulev2.condition;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.riskplatform.common.error.ValidationException;

/**
 * 条件树 JSON 序列化 / 反序列化（R2.1，对齐 design.md {@code condition_json} 结构）。
 *
 * <p>使用 Jackson（现有依赖，经 spring-boot-starter-web 传递引入）将 {@link ConditionNode}
 * 与 {@code condition_json} 字符串互转。反序列化后会触发 {@link ConditionNode#validate()}
 * 完成结构与运算符适配校验（R2.2/R2.4）。
 *
 * <p>兼容历史紧凑格式 {@code {"op":"GT","field":"txn_amount","value":50000}}（种子数据/旧版），
 * 读取时自动迁移为 {@code LEAF + left/operator/right} 结构。
 *
 * <p>编译为 Aviator 表达式在 ConditionCompiler（任务 4.3）实现，本类只做 JSON 映射。
 */
public final class ConditionTreeCodec {

    /** 共享的 Jackson 映射器：忽略未知字段，保证向后兼容。 */
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private ConditionTreeCodec() {
    }

    /**
     * 将条件树序列化为 {@code condition_json} 字符串。
     *
     * @param root 条件树根节点
     * @return JSON 字符串
     * @throws ValidationException root 为空或序列化失败时抛出
     */
    public static String toJson(ConditionNode root) {
        if (root == null) {
            throw ValidationException.builder().field("condition", "条件树不能为空").build();
        }
        try {
            return MAPPER.writeValueAsString(root);
        } catch (Exception e) {
            throw ValidationException.builder()
                    .field("condition", "条件树序列化失败：" + e.getMessage())
                    .build();
        }
    }

    /**
     * 从 {@code condition_json} 字符串反序列化为条件树，并完成结构与适配校验。
     *
     * @param json condition_json 字符串
     * @return 校验通过的条件树根节点
     * @throws ValidationException JSON 为空、格式错误或校验不通过时抛出
     */
    public static ConditionNode fromJson(String json) {
        if (json == null || json.isBlank()) {
            throw ValidationException.builder().field("condition", "条件树 JSON 不能为空").build();
        }
        ConditionNode root;
        try {
            JsonNode tree = MAPPER.readTree(json);
            root = parseTreeNode(tree);
        } catch (ValidationException ve) {
            throw ve;
        } catch (Exception e) {
            // 紧凑构造器在反序列化期抛出的字段级校验异常会被 Jackson 包装，向上查找并原样透出
            ValidationException ve = unwrapValidation(e);
            if (ve != null) {
                throw ve;
            }
            throw ValidationException.builder()
                    .field("condition", "条件树 JSON 解析失败：" + e.getMessage())
                    .build();
        }
        root.validate();
        return root;
    }

    private static ConditionNode parseTreeNode(JsonNode tree) {
        if (isLegacyCompact(tree)) {
            return migrateLegacyCompact(tree);
        }
        try {
            return MAPPER.treeToValue(tree, ConditionNode.class);
        } catch (Exception ex) {
            if (isLegacyCompact(tree)) {
                return migrateLegacyCompact(tree);
            }
            if (ex instanceof RuntimeException re) {
                throw re;
            }
            throw new IllegalStateException(ex);
        }
    }

    /**
     * 判断是否为历史紧凑叶子格式（{@code op} 为 GT/EQ 等运算符，而非 AND/OR/LEAF）。
     */
    static boolean isLegacyCompact(JsonNode node) {
        if (node == null || !node.isObject()) {
            return false;
        }
        JsonNode opNode = node.get("op");
        if (opNode == null || !opNode.isTextual()) {
            return false;
        }
        String op = opNode.asText();
        if ("AND".equals(op) || "OR".equals(op) || "NOT".equals(op) || "LEAF".equals(op)) {
            return false;
        }
        JsonNode fieldNode = node.get("field");
        if (fieldNode == null || fieldNode.isNull() || !fieldNode.isTextual()
                || fieldNode.asText().isBlank()) {
            return false;
        }
        if (!node.has("value") || node.get("value").isNull()) {
            return false;
        }
        try {
            Operator.valueOf(op);
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    /** 将历史紧凑格式迁移为单叶子条件树。 */
    static ConditionNode migrateLegacyCompact(JsonNode node) {
        String field = node.path("field").asText(null);
        if (field == null || field.isBlank()) {
            throw ValidationException.builder().field("condition.field", "历史格式 field 必填").build();
        }
        Operator operator;
        try {
            operator = Operator.valueOf(node.path("op").asText());
        } catch (IllegalArgumentException e) {
            throw ValidationException.builder()
                    .field("condition.op", "历史格式运算符非法：" + node.path("op").asText())
                    .build();
        }
        JsonNode valueNode = node.get("value");
        if (valueNode == null || valueNode.isNull()) {
            throw ValidationException.builder().field("condition.value", "历史格式 value 必填").build();
        }
        Object value = MAPPER.convertValue(valueNode, Object.class);
        DataType dataType = inferDataType(valueNode);
        Variable left = new Variable(VariableSource.FIELD, field, dataType);
        RightOperand right = RightOperand.ofConst(value);
        return ConditionNode.leaf(left, operator, right);
    }

    private static DataType inferDataType(JsonNode valueNode) {
        if (valueNode.isBoolean()) {
            return DataType.BOOLEAN;
        }
        if (valueNode.isNumber()) {
            return DataType.NUMBER;
        }
        if (valueNode.isArray()) {
            return DataType.COLLECTION;
        }
        return DataType.STRING;
    }

    /**
     * 在异常因果链中查找 {@link ValidationException}（紧凑构造器抛出的校验异常会被 Jackson 包装）。
     *
     * @param e 顶层异常
     * @return 找到的 ValidationException，否则 {@code null}
     */
    private static ValidationException unwrapValidation(Throwable e) {
        Throwable cur = e;
        while (cur != null) {
            if (cur instanceof ValidationException ve) {
                return ve;
            }
            cur = cur.getCause();
        }
        return null;
    }
}
