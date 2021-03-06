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

package stroom.security.server;

import org.springframework.context.annotation.Scope;
import stroom.security.Insecure;
import stroom.security.shared.LoginAction;
import stroom.security.shared.User;
import stroom.security.shared.UserAndPermissions;
import stroom.security.shared.UserRef;
import stroom.task.server.AbstractTaskHandler;
import stroom.task.server.TaskHandlerBean;
import stroom.util.spring.StroomScope;

import javax.annotation.Resource;
import java.util.Set;

@TaskHandlerBean(task = LoginAction.class)
@Scope(value = StroomScope.TASK)
@Insecure
public class LoginHandler extends AbstractTaskHandler<LoginAction, UserAndPermissions> {
    @Resource
    private AuthenticationService authenticationService;
    @Resource
    private UserPermissionsCache userPermissionCache;

    @Override
    public UserAndPermissions exec(final LoginAction task) {
        final User user = authenticationService.login(task.getUserName(), task.getPassword());
        if (user == null) {
            return null;
        }

        // Get permissions for this user.
        final UserPermissions userPermissions = userPermissionCache.get(UserRef.create(user));
        if (userPermissions == null) {
            return null;
        }
        final Set<String> appPermissionSet = userPermissions.getAppPermissionSet();
        return new UserAndPermissions(user, appPermissionSet);
    }
}
