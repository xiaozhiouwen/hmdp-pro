package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.Result;
import com.hmdp.entity.BlogComments;

/**
 * 探店笔记评论服务
 */
public interface IBlogCommentsService extends IService<BlogComments> {

    /**
     * 发布评论
     */
    Result saveComment(BlogComments comment);

    /**
     * 查询某篇笔记下的评论
     */
    Result queryCommentsOfBlog(Long blogId, Integer current);

    /**
     * 给评论点赞
     */
    Result likeComment(Long id);

    /**
     * 查询我收到的评论（别人评论我的笔记）
     */
    Result queryCommentsOfMe();
}
