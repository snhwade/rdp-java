package com.riskplatform.ruleconfig.domain.org;

import com.riskplatform.common.error.ValidationException;

import java.util.regex.Pattern;

/**
 * 机构聚合根（R11.1/R11.2）。
 *
 * <p>不变式：
 * <ul>
 *   <li>name 长度 1..128（R11.1）</li>
 *   <li>code 长度 1..64 且仅由字母、数字、下划线组成（R11.1）</li>
 *   <li>创建时默认状态 ENABLED</li>
 *   <li>物化路径 path 形如 {@code /1/4/}：根机构为 {@code /{id}/}，
 *       子机构为 {@code 父path + {id}/}（R11.2）</li>
 * </ul>
 *
 * <p>本类为纯领域对象，不依赖框架。path 在应用服务计算 id 后通过
 * {@link #assignIdAndPath} 回填（自增主键，path 依赖 id）。
 */
public class Org {

    public static final int NAME_MAX = 128;
    public static final int CODE_MAX = 64;
    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Za-z0-9_]+$");

    private Long id;
    private String code;
    private String name;
    /** 父机构 id；null 表示根机构。 */
    private Long parentId;
    /** 物化路径，如 /1/4/。 */
    private String path;
    private OrgStatus status;

    private Org() {
    }

    /** 工厂方法：创建一个启用状态的机构（尚未分配 id 与 path），并执行输入校验。 */
    public static Org create(String code, String name, Long parentId) {
        Org org = new Org();
        org.code = code;
        org.name = name;
        org.parentId = parentId;
        org.status = OrgStatus.ENABLED;
        org.validate();
        return org;
    }

    /** 从持久化重建（不重复校验）。 */
    public static Org rehydrate(Long id, String code, String name, Long parentId, String path, OrgStatus status) {
        Org org = new Org();
        org.id = id;
        org.code = code;
        org.name = name;
        org.parentId = parentId;
        org.path = path;
        org.status = status;
        return org;
    }

    /** 校验 name 与 code，违反不变式时抛出聚合字段错误。 */
    public void validate() {
        ValidationException.Builder errors = ValidationException.builder();
        if (name == null || name.isEmpty()) {
            errors.field("name", "必填");
        } else if (name.length() > NAME_MAX) {
            errors.field("name", "长度不能超过 " + NAME_MAX + " 个字符");
        }
        if (code == null || code.isEmpty()) {
            errors.field("code", "必填");
        } else if (code.length() > CODE_MAX) {
            errors.field("code", "长度不能超过 " + CODE_MAX + " 个字符");
        } else if (!CODE_PATTERN.matcher(code).matches()) {
            errors.field("code", "只能包含字母、数字与下划线");
        }
        errors.throwIfAny();
    }

    /**
     * 分配自增 id 并计算物化路径（R11.2）。
     *
     * <p>根机构（parentPath 为 null/空）path = {@code /{id}/}；
     * 子机构 path = {@code 父path + {id}/}（父 path 已以 / 结尾）。
     *
     * @param id         数据库分配的自增主键
     * @param parentPath 父机构的物化路径（根机构传 null）
     */
    public void assignIdAndPath(Long id, String parentPath) {
        this.id = id;
        if (parentPath == null || parentPath.isEmpty()) {
            this.path = "/" + id + "/";
        } else {
            this.path = parentPath + id + "/";
        }
    }

    /**
     * 判定本机构是否落入「机构 A 含下级」范围内（R11.2/R11.4，对应 Property 4）。
     *
     * <p>当且仅当本机构 path 以机构 A 的 path 为前缀（含等于自身）。
     *
     * @param orgAPath 机构 A 的物化路径，如 {@code /1/4/}
     */
    public boolean isWithinSubtreeOf(String orgAPath) {
        if (orgAPath == null || orgAPath.isEmpty() || this.path == null) {
            return false;
        }
        return this.path.startsWith(orgAPath);
    }

    /** 禁用。 */
    public void disable() {
        this.status = OrgStatus.DISABLED;
    }

    /** 启用。 */
    public void enable() {
        this.status = OrgStatus.ENABLED;
    }

    public boolean isEnabled() {
        return this.status == OrgStatus.ENABLED;
    }

    public boolean isRoot() {
        return this.parentId == null;
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public Long getParentId() {
        return parentId;
    }

    public String getPath() {
        return path;
    }

    public OrgStatus getStatus() {
        return status;
    }

    /** 更新名称（不改 code/path/层级），用于编辑（R11.1）。 */
    public void rename(String name) {
        this.name = name;
        ValidationException.Builder errors = ValidationException.builder();
        if (name == null || name.isEmpty()) {
            errors.field("name", "必填");
        } else if (name.length() > NAME_MAX) {
            errors.field("name", "长度不能超过 " + NAME_MAX + " 个字符");
        }
        errors.throwIfAny();
    }
}
