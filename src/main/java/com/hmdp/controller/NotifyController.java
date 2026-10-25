package com.hmdp.controller;

import com.hmdp.dto.Result;
import com.hmdp.service.INotifyService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * 消息通知控制器
 */
@RestController
@RequestMapping("/notify")
public class NotifyController {

    @Resource
    private INotifyService notifyService;

    /**
     * 滚动分页查询通知列表
     */
    @GetMapping("/list")
    public Result queryNotifyList(
            @RequestParam(value = "max", required = false) Long max,
            @RequestParam(value = "offset", required = false) Integer offset) {
        return notifyService.queryNotifyList(max, offset);
    }
}
