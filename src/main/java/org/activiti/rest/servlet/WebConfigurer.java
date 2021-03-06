package org.activiti.rest.servlet;

import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.EnumSet;
import java.util.Enumeration;

import javax.servlet.DispatcherType;
import javax.servlet.FilterRegistration;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.ServletRegistration;

import org.activiti.explorer.conf.ApplicationConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;
import org.springframework.web.filter.DelegatingFilterProxy;
import org.springframework.web.servlet.DispatcherServlet;

/**
 * Configuration of web application with Servlet 3.0 APIs.
 */
public class WebConfigurer implements ServletContextListener {
	
  private final Logger log = LoggerFactory.getLogger(WebConfigurer.class);

  public AnnotationConfigWebApplicationContext context;
  
  public void setContext(AnnotationConfigWebApplicationContext context) {
    this.context = context;
  }
  
  @Override
  public void contextInitialized(ServletContextEvent sce) {
    ServletContext servletContext = sce.getServletContext();

    log.debug("Configuring Spring root application context (rest-api)");
    
    AnnotationConfigWebApplicationContext rootContext = null;
    
    if (context == null) {
        rootContext = new AnnotationConfigWebApplicationContext();
        rootContext.register(ApplicationConfiguration.class);
        rootContext.refresh();
    } else {
        rootContext = context;
    }

    servletContext.setAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, rootContext);

    EnumSet<DispatcherType> disps = EnumSet.of(DispatcherType.REQUEST, DispatcherType.FORWARD, DispatcherType.ASYNC);

    initSpring(servletContext, rootContext);
    initSpringSecurity(servletContext, disps);

    log.debug("Web application fully configured (rest-api)");
  }

  /**
   * Initializes Spring and Spring MVC.
   */
  private ServletRegistration.Dynamic initSpring(ServletContext servletContext, AnnotationConfigWebApplicationContext rootContext) {
    log.debug("Configuring Spring Web application context (rest-api)");
    AnnotationConfigWebApplicationContext dispatcherServletConfiguration = new AnnotationConfigWebApplicationContext();
    dispatcherServletConfiguration.setParent(rootContext);
    dispatcherServletConfiguration.register(DispatcherServletConfiguration.class);

    log.debug("Registering Spring MVC Servlet (rest-api)");
    ServletRegistration.Dynamic dispatcherServlet = servletContext.addServlet("dispatcher-rest", new DispatcherServlet(dispatcherServletConfiguration));
    dispatcherServlet.addMapping("/service/rest/*");
    dispatcherServlet.setLoadOnStartup(1);
    dispatcherServlet.setAsyncSupported(true);
    
    return dispatcherServlet;
  }

  /**
   * Initializes Spring Security.
   */
  private void initSpringSecurity(ServletContext servletContext, EnumSet<DispatcherType> disps) {
    log.debug("Registering Spring Security Filter (rest-api)");
    FilterRegistration.Dynamic springSecurityFilter = servletContext.addFilter("springSecurityFilterChain", new DelegatingFilterProxy());

    springSecurityFilter.addMappingForUrlPatterns(disps, false, "/service/rest/*");
    springSecurityFilter.setAsyncSupported(true);
  }

  @Override
  public void contextDestroyed(ServletContextEvent sce) {
    log.info("Destroying Web application (rest-api)");
    WebApplicationContext ac = WebApplicationContextUtils.getRequiredWebApplicationContext(sce.getServletContext());
    AnnotationConfigWebApplicationContext gwac = (AnnotationConfigWebApplicationContext) ac;
    gwac.close();
    log.debug("Web application destroyed (rest-api)");
    
    // This manually deregisters JDBC driver, which prevents Tomcat 7 from complaining about memory leaks wrto this class
    Enumeration<Driver> drivers = DriverManager.getDrivers();
    while (drivers.hasMoreElements()) {
        Driver driver = drivers.nextElement();
        try {
            DriverManager.deregisterDriver(driver);
            log.info(String.format("deregistering jdbc driver: %s", driver));
        } catch (SQLException e) {
            log.error(String.format("Error deregistering driver %s", driver), e);
        }
    }
  }
}
