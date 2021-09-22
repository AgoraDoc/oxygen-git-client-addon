package com.oxygenxml.git.utils;

import java.io.FilePermission;
import java.io.IOException;
import java.io.StringWriter;
import java.security.AccessControlException;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Iterator;

import org.apache.log4j.Logger;
import org.apache.log4j.SimpleLayout;
import org.apache.log4j.WriterAppender;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.ConfigurationSource;
import org.eclipse.jgit.util.FS;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * Logging setup tests. 
 */
public class Log4jUtilTest {
  
  private WriterAppender newAppender;
  private StringWriter writer;

  @Before
  public void setUp() throws Exception {
    newAppender = new WriterAppender();
    newAppender.setLayout(new SimpleLayout());
    writer = new StringWriter();
    newAppender.setWriter(writer);
    
    Logger.getRootLogger().addAppender(newAppender);
    
    Log4jUtil.setupLog4JLogger();
  }
  
  @After
  public void tearDown() throws Exception {
    Logger.getRootLogger().removeAppender(newAppender);
  }

  /**
   * EXM-44131 Tests that we filter a specific AccessControlException error.
   * 
   * @throws Exception
   */
  @Test
  public void testLogFilter() throws Exception {

    //=====================
    // An exception is not an AccessControlException. It should pass.
    //=====================
    
    Exception ex = new IOException("A test");
    Logger logger = Logger.getLogger(FS.class);
    logger.error(ex,  ex);
    
    LoggerContext context = LoggerContext.getContext(false);
    Configuration config = context.getConfiguration();
    Collection<Appender> values = config.getAppenders().values();
    for (Iterator iterator = values.iterator(); iterator.hasNext();) {
      Appender appender = (Appender) iterator.next();
      System.out.println("appender: " + appender.getName());
    }
    ConfigurationSource configurationSource = config.getConfigurationSource();
    System.out.println("configurationSource: " + configurationSource.getLocation());
    Filter filter = config.getFilter();
    System.out.println("filter: " + filter);
    
    Assert.assertTrue("The log must pass: " + writer.toString(), writer.toString().startsWith("ERROR - java.io.IOException: A test"));

    //=====================
    // An AccessControlException issued through a specific class logger. It should be filtered.
    //=====================
    writer.getBuffer().setLength(0);
    FilePermission perm = new FilePermission(".probe-64fe0316-10fa-4fa1-b163-d79366318e4b", "write");
    ex = new AccessControlException("access denied "+ perm, perm);
    Logger.getLogger(FS.class).error(ex,  ex);
    
    Assert.assertEquals("This exception should be filtered from the logger: " + writer.toString(), "", writer.toString());
    

    //=====================
    // An AccessControlException issued through another class logger. It should pass.
    //=====================
    writer.getBuffer().setLength(0);
    perm = new FilePermission(".probe-64fe0316-10fa-4fa1-b163-d79366318e4b", "write");
    ex = new AccessControlException("access denied "+ perm, perm);
    Logger.getLogger(Log4jUtilTest.class).error(ex,  ex);
    
    Assert.assertTrue("The log must pass: " + writer.toString(), writer.toString().startsWith("ERROR - java.security.AccessControlException: access denied (\"java.io.FilePermission\" \".probe-64fe0316-10fa-4fa1-b163-d79366318e4b\" \"write\")"));
  }
}
