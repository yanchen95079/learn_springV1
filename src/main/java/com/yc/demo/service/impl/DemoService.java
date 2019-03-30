package com.yc.demo.service.impl;


import com.yc.demo.service.IDemoService;
import com.yc.springmvc.annotation.YCService;

/**
 * 核心业务逻辑
 */
@YCService
public class DemoService implements IDemoService {

	public String get(String name) {
		return "My name is " + name;
	}

}
