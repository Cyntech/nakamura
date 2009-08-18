/*
 * Licensed to the Sakai Foundation (SF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The SF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.sakaiproject.kernel.comments.servlets;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.Resource;
import org.sakaiproject.kernel.resource.AbstractVirtualPathServlet;
import org.sakaiproject.kernel.util.PathUtils;
import org.sakaiproject.kernel.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 
 * Rewriting paths liks /path/to/store/commentid to
 * /path/to/store/path/to/the/comment
 * 
 * @scr.component metatype="no" immediate="true"
 * @scr.service interface="javax.servlet.Servlet"
 * @scr.property name="sling.servlet.resourceTypes" value="sakai/commentsstore"
 * @scr.property name="sling.servlet.methods" values.0="POST" values.1="PUT"
 *               values.2="DELETE" values.3="GET"
 */
public class CommentsServlet extends AbstractVirtualPathServlet {

  /**
   * 
   */
  private static final long serialVersionUID = 1L;
  public static final Logger LOGGER = LoggerFactory
      .getLogger(CommentsServlet.class);

  @Override
  protected String getTargetPath(Resource baseResource,
      SlingHttpServletRequest request, SlingHttpServletResponse response,
      String realPath, String virtualPath) {
    // path will be of form /path/to/store/cid.

    if (virtualPath != "" && virtualPath != null) {
      String[] parts = StringUtils.split(virtualPath, '.');

      return PathUtils.toInternalHashedPath(realPath, parts[0], "");
    }
    return realPath;
  }
}