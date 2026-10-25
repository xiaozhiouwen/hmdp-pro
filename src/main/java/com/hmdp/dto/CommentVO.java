package com.hmdp.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 评论展示对象：评论内容 + 评论人信息
 */
@Data
public class CommentVO {

    private Long id;
    private Long userId;
    private Long blogId;
    private Long parentId;
    private Long answerId;
    private String content;
    private Integer liked;
    private LocalDateTime createTime;

    /** 评论人昵称 */
    private String nickName;
    /** 评论人头像 */
    private String icon;
    /** 评论所属笔记标题 */
    private String blogTitle;
}
