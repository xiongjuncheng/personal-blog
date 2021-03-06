package com.june.web.controller.admin;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.json.JSONObject;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.june.model.BlogType;
import com.june.model.PageBean;
import com.june.service.BlogService;
import com.june.service.BlogTypeService;
import com.june.util.Constants;
import com.june.util.PageUtils;
import com.june.util.ResponseUtils;

/**
 * 博客类别Controller
 */
@Controller
@RequestMapping("/blogType")
public class BlogTypeAdminController {

	@Resource
	private BlogTypeService blogTypeService;
	
	@Resource
	private BlogService blogService;

	@RequestMapping("/list")
	public String list(@RequestParam(defaultValue = "1") Integer page,
			@RequestParam(defaultValue = Constants.BACK_PAGE_SIZE + 3 + "") Integer pageSize,
			Model model, HttpServletRequest request) {
		int totalCount = blogTypeService.getCount();
		PageBean pageBean = new PageBean(totalCount, page, pageSize);
		Map<String, Object> params = new HashMap<>(2);
		params.put("start", pageBean.getStart());
		params.put("size", pageSize);
		List<BlogType> blogTypeList = blogTypeService.getTypeList(params);
		blogTypeList.forEach(blogType -> {
			Map<String, Object> types = new HashMap<>(1);
			types.put("typeId", blogType.getTypeId());
			Integer blogCount = blogService.getCount(types);
			blogType.setBlogCount(blogCount);
		});
		model.addAttribute("pagination", pageBean);

		String targetUrl = request.getContextPath() + "/blogType/list.do";
		String pageCode = PageUtils.genPagination(targetUrl, pageBean, "");
		model.addAttribute("pageCode", pageCode);
		model.addAttribute("entry", params);
		model.addAttribute("blogTypeList", blogTypeList);
		return "blogType/list";
	}

	@RequestMapping("/toAdd")
	public String toAdd() {
		return "blogType/add";
	}

	@RequestMapping("/toUpdate")
	public String toUpdate(Integer id,Model model) {
		model.addAttribute("blogType", blogTypeService.findById(id));
		return "blogType/update";
	}

	@RequestMapping("/add")
	public void add(BlogType blogType,Model model) {
		int result = blogTypeService.add(blogType);
		model.addAttribute("msg", result > 0 ? "保存成功" : "保存失败");
	}
	
	@RequestMapping("/update")
	public void update(BlogType blogType,Model model) {
		int result = blogTypeService.update(blogType);
		model.addAttribute("msg", result > 0 ? "保存成功" : "保存失败");
	}
	
	@RequestMapping("/search")
	public void search(Integer id,HttpServletResponse response){
		JSONObject jsonObj = new JSONObject();
		Map<String, Object> map = new HashMap<>(1);
		map.put("typeId", id);
		jsonObj.put("count", blogService.getCount(map));
		ResponseUtils.writeJson(response, jsonObj.toString());
	}

	//只删除类别
	@RequestMapping("/delete")
	public String delete(Integer id) {
		blogTypeService.delete(id);
		return "redirect:/blogType/list.do";
	}

	//删除的同时将相关博客的类别置为默认分类
	@RequestMapping("/batch_delete")
	public String batchDelete(Integer id) throws IOException {
		blogTypeService.batchDelete(id);
		return "redirect:/blogType/list.do";
	}
}
