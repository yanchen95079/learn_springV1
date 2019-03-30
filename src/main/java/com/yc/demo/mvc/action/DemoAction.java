package com.yc.demo.mvc.action;



import com.yc.demo.service.IDemoService;
import com.yc.springmvc.annotation.YCAutowired;
import com.yc.springmvc.annotation.YCController;
import com.yc.springmvc.annotation.YCRequestMapping;
import com.yc.springmvc.annotation.YCRequestParam;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@YCController
@YCRequestMapping("/demo")
public class DemoAction {

  	@YCAutowired
	private IDemoService demoService;

	@YCRequestMapping("/query")
	public void query( HttpServletResponse resp,
					  @YCRequestParam("name") String name){
		String result = "name= " + name;
		try {
			resp.getWriter().write(result);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}



}
