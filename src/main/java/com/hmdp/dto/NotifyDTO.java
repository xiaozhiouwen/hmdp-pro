package com.hmdp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 消息通知项，存储在 Redis ZSet（key = notify:{userId}）中，score 为时间戳
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotifyDTO {

    /** 通知类型：1-点赞笔记，2-关注，3-评论 */
    public static final Integer TYPE_LIKE = 1;
    public static final Integer TYPE_FOLLOW = 2;
    public static final Integer TYPE_COMMENT = 3;

    /** 通知类型 */
    private Integer type;
    /** 业务id：点赞时为笔记id，关注时为触发者id */
    private Long bizId;
    /** 触发者id */
    private Long fromUserId;
    /** 触发者昵称 */
    private String fromName;
    /** 触发者头像 */
    private String fromIcon;
    /** 附加内容：点赞时为笔记标题，关注时为空 */
    private String content;
    /** 通知时间 */
    private Long createTime;
}
