package com.hmdp.controller;

import com.hmdp.dto.Result;
import com.hmdp.entity.BlogComments;
import com.hmdp.service.IBlogCommentsService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * 探店笔记评论控制器
 */
@RestController
@RequestMapping("/blog-comments")
public class BlogCommentsController {

    @Resource
    private IBlogCommentsService blogCommentsService;

    /**
     * 发布评论
     */
    @PostMapping
    public Result saveComment(@RequestBody BlogComments comment) {
        return blogCommentsService.saveComment(comment);
    }

    /**
     * 查询笔记下的评论列表
     */
    @GetMapping("/of/blog/{id}")
    public Result queryCommentsOfBlog(@PathVariable("id") Long blogId,
                                      @RequestParam(value = "current", defaultValue = "1") Integer current) {
        return blogCommentsService.queryCommentsOfBlog(blogId, current);
    }

    /**
     * 评论点赞
     */
    @PutMapping("/like/{id}")
    public Result likeComment(@PathVariable("id") Long id) {
        return blogCommentsService.likeComment(id);
    }

    /**
     * 查询我收到的评论
     */
    @GetMapping("/of/me")
    public Result queryCommentsOfMe() {
        return blogCommentsService.queryCommentsOfMe();
    }
}
