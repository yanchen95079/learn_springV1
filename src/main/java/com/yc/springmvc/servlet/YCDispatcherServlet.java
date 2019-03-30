package com.yc.springmvc.servlet;

import com.yc.pojo.HandlerMapping;
import com.yc.springmvc.annotation.YCAutowired;
import com.yc.springmvc.annotation.YCController;
import com.yc.springmvc.annotation.YCRequestMapping;
import com.yc.springmvc.annotation.YCService;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 此类作为启动入口
 * @author Tom
 *
 */
public class YCDispatcherServlet extends HttpServlet {
	private static final String LOCATION = "contextConfigLocation";
	private Properties properties=new Properties();
	private List<String> classNames=new ArrayList<String>();
    private Map<String,Object> ioc = new ConcurrentHashMap<String, Object>();
    private List<HandlerMapping> handlers = new ArrayList<HandlerMapping>();
	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		this.doPost(req,resp);
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String url = req.getRequestURI();
        String contextPath = req.getContextPath();
        url = url.replaceAll(contextPath,"").replaceAll("/+","/");
        if(this.handlers.isEmpty()){
            resp.getWriter().write("404");
            return;
        }
        for (HandlerMapping handler : this.handlers) {
            if(url.equals(handler.getUrl())){
                Class<?>[] parameterTypes = handler.getMethod().getParameterTypes();
                //保存所有需要自动赋值的参数值
                Object [] paramValues = new Object[parameterTypes.length];
                Map<String,String[]> params = req.getParameterMap();
                for (Entry<String, String[]> param : params.entrySet()) {
                    String value = Arrays.toString(param.getValue()).replaceAll("\\[|\\]", "").replaceAll(",\\s", ",");

                    //如果找到匹配的对象，则开始填充参数值
                    if(!handler.getParamIndexMapping().containsKey(param.getKey())){continue;}
                    int index = handler.getParamIndexMapping().get(param.getKey());
                    paramValues[index] = value;
                }
                //设置方法中的request和response对象
                Integer reqIndex=handler.getParamIndexMapping().get(HttpServletRequest.class.getName());
                if(reqIndex!=null){
                    paramValues[reqIndex] = req;
                }
                Integer respIndex = handler.getParamIndexMapping().get(HttpServletResponse.class.getName());

                if(respIndex!=null){
                    paramValues[respIndex] = resp;
                }


                try {
                    handler.getMethod().invoke(handler.getInstance(), paramValues);
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                } catch (InvocationTargetException e) {
                    e.printStackTrace();
                }
            }

        }
	}

	@Override
	public void init(ServletConfig config) throws ServletException {
		//加载
		doLoad(config.getInitParameter(LOCATION));
		//扫描
		doScanner(properties.getProperty("scanPackage"));
		//初始化ioc
        doIoc();
		//依赖注入
        doAutowired();
        //构造HandlerMapping
        initHandlerMapping();
	}

    private void initHandlerMapping() {
        if(ioc.isEmpty()){ return; }
        for (Entry<String, Object> entry : ioc.entrySet()) {
            Class<?> clazz = entry.getValue().getClass();
            if(!clazz.isAnnotationPresent(YCController.class)){ continue; }
            String baseUrl="";
            if(clazz.isAnnotationPresent(YCRequestMapping.class)){
                baseUrl=clazz.getAnnotation(YCRequestMapping.class).value().trim();
            }

            for (Method method : entry.getValue().getClass().getMethods()) {
                if(!method.isAnnotationPresent(YCRequestMapping.class)){ continue; }
                String url=("/"+baseUrl+"/"+method.getAnnotation(YCRequestMapping.class).value()).replaceAll("/+","/");
                handlers.add(new HandlerMapping(url,entry.getValue(),method));
                System.out.println("Mapped :" + url + "," + method);
            }
        }
    }

    private void doAutowired() {
	    if(ioc.isEmpty()){return;}
        for (Entry<String, Object> entry : ioc.entrySet()) {
            Field[] declaredFields = entry.getValue().getClass().getDeclaredFields();
            for (Field field : declaredFields) {
                if(!field.isAnnotationPresent(YCAutowired.class)){continue;}
                String value = field.getAnnotation(YCAutowired.class).value();
                if("".equals(value.trim())){
                    value=field.getType().getName();
                }
                field.setAccessible(true);
                try {
                    field.set(entry.getValue(),ioc.get(value));
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void doIoc() {
	    if(classNames.size()==0){return;}
	    try {
            for (String className : classNames) {
                Class<?> clazz = Class.forName(className);
                if(clazz.isAnnotationPresent(YCController.class)){
                    ioc.put(lowerFirst(clazz.getSimpleName()),clazz.newInstance());
                }
                if(clazz.isAnnotationPresent(YCService.class)){
                    String name = clazz.getAnnotation(YCService.class).value();
                    //如果用户设置了名字，就用用户自己设置
                    if(!"".equals(name.trim())){
                        ioc.put(name, clazz.newInstance());
                        continue;
                    }
                    for (Class<?> aClass : clazz.getInterfaces()) {
                        ioc.put(aClass.getName(),clazz.newInstance());
                    }
                }

            }
        }catch (Exception e){
	        e.printStackTrace();
        }

    }

    private void doScanner(String scanPackage) {
        URL url = this.getClass().getClassLoader().getResource("/" +scanPackage.replaceAll("\\.","/"));
        File file=new File(url.getFile());
        for (File f : file.listFiles()) {
            if(f.isDirectory()){
                doScanner(scanPackage+"."+f.getName());
            }else {
                classNames.add(scanPackage+"."+f.getName().replaceAll(".class","").trim());
            }

        }
    }

	private void doLoad(String initParameter) {
		InputStream is=this.getClass().getClassLoader().getResourceAsStream(initParameter);
		try {
			properties.load(is);
		} catch (IOException e) {
			e.printStackTrace();
		}finally {
			if(is!=null){
				try {
					is.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}
    /**
     * 首字母小母
     * @param str
     * @return
     */
    private String lowerFirst(String str){
        char [] chars = str.toCharArray();
        chars[0] += 32;
        return String.valueOf(chars);
    }

}
