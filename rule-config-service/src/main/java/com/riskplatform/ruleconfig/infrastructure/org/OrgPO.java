package com.riskplatform.ruleconfig.infrastructure.org;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * org 表持久化对象（V13）。
 *
 * <p>列：id, code(unique), name, parent_id(null=根), path(物化路径), status(VARCHAR),
 * create_user/create_time/update_user/update_time（审计列，时间由 DB 默认值维护）。
 */
@TableName("org")
public class OrgPO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private String name;
    @TableField("parent_id")
    private Long parentId;
    private String path;
    /** ENABLED / DISABLED */
    private String status;
    @TableField("create_user")
    private String createUser;
    @TableField(value = "create_time")
    private LocalDateTime createTime;
    @TableField("update_user")
    private String updateUser;
    @TableField(value = "update_time")
    private LocalDateTime updateTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCreateUser() {
        return createUser;
    }

    public void setCreateUser(String createUser) {
        this.createUser = createUser;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public String getUpdateUser() {
        return updateUser;
    }

    public void setUpdateUser(String updateUser) {
        this.updateUser = updateUser;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }
}
