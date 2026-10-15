package com.meiyetec.controller;

import com.meiyetec.dto.CategoryDTO;
import com.meiyetec.entity.Category;
import com.meiyetec.annotation.SysOpLog;
import com.meiyetec.service.CategoryService;
import com.meiyetec.util.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/category")
@RequiredArgsConstructor
@SysOpLog(module = "商品")
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping("/list")
    public Result<List<Category>> list() {
        return Result.success(categoryService.list());
    }

    @GetMapping("/tree")
    public Result<List<Category>> tree() {
        List<Category> all = categoryService.list();
        List<Category> tree = buildTree(all);
        return Result.success(tree);
    }

    @PostMapping("/sort")
    public Result<Void> sort(@RequestBody List<Long> ids) {
        for (int i = 0; i < ids.size(); i++) {
            Category c = categoryService.getById(ids.get(i));
            if (c != null) {
                c.setSortOrder(i);
                categoryService.updateById(c);
            }
        }
        return Result.success("排序更新成功", null);
    }

    @GetMapping("/{id:\\d+}")
    public Result<Category> getById(@PathVariable Long id) {
        return Result.success(categoryService.getById(id));
    }

    @PostMapping
    public Result<Void> create(@Valid @RequestBody CategoryDTO dto) {
        Category category = new Category();
        org.springframework.beans.BeanUtils.copyProperties(dto, category);
        categoryService.save(category);
        return Result.success("创建成功", null);
    }

    @PutMapping
    public Result<Void> update(@Valid @RequestBody CategoryDTO dto) {
        Category category = new Category();
        org.springframework.beans.BeanUtils.copyProperties(dto, category);
        categoryService.updateById(category);
        return Result.success("更新成功", null);
    }

    @DeleteMapping("/{id:\\d+}")
    public Result<Void> delete(@PathVariable Long id) {
        categoryService.removeById(id);
        return Result.success("删除成功", null);
    }

    private List<Category> buildTree(List<Category> all) {
        return all.stream()
                .filter(c -> c.getParentId() == 0)
                .peek(p -> p.setChildren(getChildren(p.getId(), all)))
                .toList();
    }

    private List<Category> getChildren(Long parentId, List<Category> all) {
        return all.stream()
                .filter(c -> parentId.equals(c.getParentId()))
                .peek(c -> c.setChildren(getChildren(c.getId(), all)))
                .toList();
    }
}


