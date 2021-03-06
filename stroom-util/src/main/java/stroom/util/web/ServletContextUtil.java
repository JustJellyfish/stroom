/*
 * Copyright 2016 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.util.web;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;

public class ServletContextUtil {
    private static final String DEFAULT_NAME = "stroom";
    private static final String WEBAPP = "webapp";

    public static final String getWARName(ServletConfig servletConfig) {
        if (servletConfig == null) {
            return DEFAULT_NAME;
        }

        return getWARName(servletConfig.getServletContext());
    }

    public static final String getWARName(ServletContext servletContext) {
        final String fullPath = servletContext.getRealPath(".");
        final String[] parts = fullPath.split("/");

        if (WEBAPP.equals(parts[parts.length - 1])) {
            return DEFAULT_NAME;
        }

        return parts[parts.length - 2];
    }
}
