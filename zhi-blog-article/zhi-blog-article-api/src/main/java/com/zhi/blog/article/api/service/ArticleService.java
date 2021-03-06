package com.zhi.blog.article.api.service;

import com.zhi.blog.common.core.model.CommonResult;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * @author Ted
 * @date 2022/5/20
 **/
@FeignClient(name = "zhi-blog-article", contextId = "article-context")
public interface ArticleService {

    @GetMapping("/zhi-blog-article/article")
    CommonResult getArticle();

    @PostMapping("/zhi-blog-article/article")
    CommonResult addArticle();
}
