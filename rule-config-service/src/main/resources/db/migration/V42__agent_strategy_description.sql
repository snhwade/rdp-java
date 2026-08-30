-- Agent 策略增加描述/备注，便于多人协作时理解策略用途
ALTER TABLE agent_strategy
    ADD COLUMN description VARCHAR(512) NULL COMMENT '策略用途说明/备注' AFTER name;
