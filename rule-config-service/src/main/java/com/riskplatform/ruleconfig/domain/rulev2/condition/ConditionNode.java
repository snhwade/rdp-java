package com.riskplatform.ruleconfig.domain.rulev2.condition;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.riskplatform.common.error.ValidationException;

import java.util.List;

/**
 * 结构化条件树节点（值对象，R2.1/R2.2/R2.3/R2.4）。
 *
 * <p>一棵条件树由分支节点与叶子节点组成，对齐 design.md 的 {@code condition_json} 结构：
 * <pre>{@code
 * {
 *   "op": "AND",
 *   "children": [
 *     { "op": "LEAF",
 *       "left":  { "source": "INDICATOR", "ref": "txn_amt_3d", "dataType": "NUMBER" },
 *       "operator": "GTE",
 *       "right": { "kind": "CONST", "value": 10000 } },
 *     { "op": "LEAF",
 *       "left":  { "source": "FIELD", "ref": "cardNo", "dataType": "STRING" },
 *       "operator": "IN",
 *       "right": { "kind": "ENUM_LIB", "enumLibCode": "BLACK_BIN" } }
 *   ]
 * }
 * }</pre>
 *
 * <p>节点语义：
 * <ul>
 *   <li>{@code AND}/{@code OR}：分支节点，持 {@code children}（至少 2 个）。</li>
 *   <li>{@code NOT}：分支节点，持 {@code children}（恰好 1 个）。</li>
 *   <li>{@code LEAF}：叶子节点，持 {@code left}（左变量）+ {@code operator} + {@code right}（右值）。</li>
 * </ul>
 *
 * <p>本类只负责领域模型与结构/适配校验；编译为 Aviator 表达式在 ConditionCompiler（任务 4.3）实现。
 *
 * @param op       节点操作类型
 * @param children 子节点（分支节点用）
 * @param left     左变量（叶子节点用）
 * @param operator 运算符（叶子节点用）
 * @param right    右值（叶子节点用）
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ConditionNode(ConditionOp op,
                            List<ConditionNode> children,
                            Variable left,
                            Operator operator,
                            RightOperand right) {

    /**
     * 创建叶子节点并完成校验（左变量 + 运算符 + 右值 + 运算符类型适配）。
     *
     * @param left     左变量
     * @param operator 运算符
     * @param right    右值
     * @return 校验通过的叶子节点
     * @throws ValidationException 结构缺失或运算符与数据类型不适配时抛出
     */
    public static ConditionNode leaf(Variable left, Operator operator, RightOperand right) {
        ConditionNode node = new ConditionNode(ConditionOp.LEAF, null, left, operator, right);
        node.validate();
        return node;
    }

    /**
     * 创建 AND 分支节点。
     *
     * @param children 子节点（至少 2 个）
     */
    public static ConditionNode and(List<ConditionNode> children) {
        ConditionNode node = new ConditionNode(ConditionOp.AND, children, null, null, null);
        node.validate();
        return node;
    }

    /**
     * 创建 OR 分支节点。
     *
     * @param children 子节点（至少 2 个）
     */
    public static ConditionNode or(List<ConditionNode> children) {
        ConditionNode node = new ConditionNode(ConditionOp.OR, children, null, null, null);
        node.validate();
        return node;
    }

    /**
     * 创建 NOT 分支节点。
     *
     * @param child 唯一子节点
     */
    public static ConditionNode not(ConditionNode child) {
        ConditionNode node = new ConditionNode(ConditionOp.NOT, child == null ? null : List.of(child),
                null, null, null);
        node.validate();
        return node;
    }

    /**
     * 递归校验整棵条件树（R2.2/R2.4）。
     *
     * <p>校验项：
     * <ul>
     *   <li>op 必填。</li>
     *   <li>分支节点：AND/OR 至少 2 个子节点；NOT 恰好 1 个子节点；不得携带叶子字段。</li>
     *   <li>叶子节点：left/operator/right 必填；不得携带 children；运算符与左变量数据类型适配（R2.2）。</li>
     * </ul>
     *
     * @throws ValidationException 任一不变式被违反时抛出
     */
    public void validate() {
        ValidationException.Builder errors = ValidationException.builder();
        validateInto(errors, "condition");
        errors.throwIfAny();
    }

    private void validateInto(ValidationException.Builder errors, String path) {
        if (op == null) {
            errors.field(path + ".op", "必填");
            return;
        }
        switch (op) {
            case LEAF -> validateLeaf(errors, path);
            case NOT -> {
                rejectLeafFields(errors, path);
                if (children == null || children.size() != 1) {
                    errors.field(path + ".children", "NOT 节点必须且仅有一个子节点");
                } else {
                    children.get(0).validateInto(errors, path + ".children[0]");
                }
            }
            case AND, OR -> {
                rejectLeafFields(errors, path);
                if (children == null || children.size() < 2) {
                    errors.field(path + ".children", op + " 节点至少需要两个子节点");
                } else {
                    for (int i = 0; i < children.size(); i++) {
                        ConditionNode child = children.get(i);
                        if (child == null) {
                            errors.field(path + ".children[" + i + "]", "子节点不能为空");
                        } else {
                            child.validateInto(errors, path + ".children[" + i + "]");
                        }
                    }
                }
            }
            default -> errors.field(path + ".op", "不支持的节点类型");
        }
    }

    private void validateLeaf(ValidationException.Builder errors, String path) {
        if (children != null && !children.isEmpty()) {
            errors.field(path + ".children", "叶子节点不应包含子节点");
        }
        if (left == null) {
            errors.field(path + ".left", "叶子节点左变量必填");
        } else {
            if (left.source() == null) {
                errors.field(path + ".left.source", "必填");
            }
            if (left.ref() == null || left.ref().isBlank()) {
                errors.field(path + ".left.ref", "必填");
            }
            if (left.dataType() == null) {
                errors.field(path + ".left.dataType", "必填");
            }
        }
        if (operator == null) {
            errors.field(path + ".operator", "叶子节点运算符必填");
        }
        if (right == null) {
            errors.field(path + ".right", "叶子节点右值必填");
        } else {
            validateRightOperand(errors, path + ".right", right);
        }
        // 运算符与左变量数据类型适配校验（R2.2）
        if (left != null && operator != null && !operator.supports(left.dataType())) {
            errors.field(path + ".operator",
                    "运算符 " + operator + " 不适配数据类型 " + left.dataType());
        }
    }

    private void validateRightOperand(ValidationException.Builder errors, String path, RightOperand right) {
        if (right.kind() == null) {
            errors.field(path + ".kind", "必填");
            return;
        }
        switch (right.kind()) {
            case CONST -> {
                if (right.value() == null) {
                    errors.field(path + ".value", "常量右值必填");
                }
            }
            case VARIABLE -> {
                if (right.variable() == null) {
                    errors.field(path + ".variable", "变量右值必填");
                }
            }
            case ENUM_LIB -> {
                if (right.enumLibCode() == null || right.enumLibCode().isBlank()) {
                    errors.field(path + ".enumLibCode", "枚举库编码必填");
                }
            }
            case IMPORT_SET -> {
                if (right.importValues() == null || right.importValues().isEmpty()) {
                    errors.field(path + ".importValues", "导入值集合不能为空");
                }
            }
            default -> errors.field(path + ".kind", "不支持的右值类型");
        }
    }

    private void rejectLeafFields(ValidationException.Builder errors, String path) {
        if (left != null) {
            errors.field(path + ".left", op + " 分支节点不应包含左变量");
        }
        if (operator != null) {
            errors.field(path + ".operator", op + " 分支节点不应包含运算符");
        }
        if (right != null) {
            errors.field(path + ".right", op + " 分支节点不应包含右值");
        }
    }
}
