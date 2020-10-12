/*
 * Copyright (c) 2008-2019 Haulmont.
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

package com.haulmont.cuba.web.app.login;

import com.haulmont.cuba.CubaProperties;
import com.haulmont.cuba.core.global.Messages;
import com.haulmont.cuba.web.security.AuthInfo;
import com.vaadin.server.VaadinServletRequest;
import com.vaadin.server.VaadinServletResponse;
import io.jmix.core.CoreProperties;
import io.jmix.core.security.ClientDetails;
import io.jmix.ui.*;
import io.jmix.ui.action.Action;
import io.jmix.ui.component.*;
import io.jmix.ui.navigation.Route;
import io.jmix.ui.screen.*;
import io.jmix.ui.security.UiLoginProperties;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;

import javax.inject.Inject;
import java.util.Locale;

import static org.springframework.security.web.authentication.rememberme.AbstractRememberMeServices.DEFAULT_PARAMETER;

/**
 * Base class for Login screen.
 */

@Route(path = "login", root = true)
@UiDescriptor("login-screen.xml")
@UiController("login")
public class LoginScreen extends Screen {

    @Inject
    protected UiLoginProperties loginProperties;
    @Inject
    protected CoreProperties coreProperties;
    @Inject
    protected CubaProperties cubaProperties;
    @Inject
    protected UiProperties uiProperties;

    @Inject
    protected Messages messages;
    @Inject
    protected Notifications notifications;
    @Inject
    protected Screens screens;
    @Inject
    private ScreenBuilders screenBuilders;

    @Inject
    protected JmixApp app;

    @Inject
    protected AuthenticationManager authenticationManager;
    @Inject
    private CompositeSessionAuthenticationStrategy authenticationStrategy;

    @Inject
    protected Image logoImage;
    @Inject
    protected TextField<String> loginField;
    @Inject
    protected CheckBox rememberMeCheckBox;
    @Inject
    protected PasswordField passwordField;
    @Inject
    protected ComboBox<Locale> localesSelect;

    @Subscribe
    protected void onInit(InitEvent event) {
        loginField.focus();

        initPoweredByLink();

        initLogoImage();

        initDefaultCredentials();

        initLocales();

        initRememberMe();

        initRememberMeLocalesBox();
    }

    protected void initPoweredByLink() {
        Component poweredByLink = getWindow().getComponent("poweredByLink");
        if (poweredByLink != null) {
            poweredByLink.setVisible(cubaProperties.isPoweredByLinkVisible());
        }
    }

    protected void initLocales() {
        localesSelect.setOptionsMap(coreProperties.getAvailableLocales());
        localesSelect.setValue(app.getLocale());

        boolean localeSelectVisible = cubaProperties.isLocaleSelectVisible();
        localesSelect.setVisible(localeSelectVisible);

        // if old layout is used
        Component localesSelectLabel = getWindow().getComponent("localesSelectLabel");
        if (localesSelectLabel != null) {
            localesSelectLabel.setVisible(localeSelectVisible);
        }

        localesSelect.addValueChangeListener(e -> {
            app.setLocale(e.getValue());

            AuthInfo authInfo = new AuthInfo(loginField.getValue(),
                    passwordField.getValue(),
                    rememberMeCheckBox.getValue());

            String screenId = UiControllerUtils.getScreenContext(this)
                    .getWindowInfo()
                    .getId();

            Screen loginScreen = screens.create(screenId, OpenMode.ROOT);

            if (loginScreen instanceof LoginScreen) {
                ((LoginScreen) loginScreen).setAuthInfo(authInfo);
            }

            loginScreen.show();
        });
    }

    protected void initLogoImage() {
        String loginLogoImagePath = messages.getMainMessage("loginWindow.logoImage", app.getLocale());
        if (StringUtils.isBlank(loginLogoImagePath) || "loginWindow.logoImage".equals(loginLogoImagePath)) {
            logoImage.setVisible(false);
        } else {
            logoImage.setSource(ThemeResource.class).setPath(loginLogoImagePath);
        }
    }

    protected void initRememberMe() {
        if (!cubaProperties.isRememberMeEnabled()) {
            rememberMeCheckBox.setValue(false);
            rememberMeCheckBox.setVisible(false);
        }
    }

    protected void initRememberMeLocalesBox() {
        Component rememberLocalesBox = getWindow().getComponent("rememberLocalesBox");
        if (rememberLocalesBox != null) {
            rememberLocalesBox.setVisible(rememberMeCheckBox.isVisible() || localesSelect.isVisible());
        }
    }

    protected void initDefaultCredentials() {
        String defaultUser = loginProperties.getDefaultUser();
        if (!StringUtils.isBlank(defaultUser) && !"<disabled>".equals(defaultUser)) {
            loginField.setValue(defaultUser);
        } else {
            loginField.setValue("");
        }

        String defaultPassw = loginProperties.getDefaultPassword();
        if (!StringUtils.isBlank(defaultPassw) && !"<disabled>".equals(defaultPassw)) {
            passwordField.setValue(defaultPassw);
        } else {
            passwordField.setValue("");
        }
    }

    @Subscribe("submit")
    public void onSubmit(Action.ActionPerformedEvent event) {
        doLogin();
    }

    protected void doLogin() {
        String username = loginField.getValue();
        String password = passwordField.getValue() != null ? passwordField.getValue() : "";

        if (StringUtils.isEmpty(username) || StringUtils.isEmpty(password)) {
            notifications.create(Notifications.NotificationType.WARNING)
                    .withCaption(messages.getMainMessage("loginWindow.emptyLoginOrPassword"))
                    .show();
            return;
        }

        try {
            Locale selectedLocale = localesSelect.getValue();
            app.setLocale(selectedLocale);

            UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(username, password);
            ClientDetails clientDetails = ClientDetails.builder()
                    .locale(localesSelect.getValue())
                    .build();
            authenticationToken.setDetails(clientDetails);

            Authentication authentication = authenticationManager.authenticate(authenticationToken);
            onSuccessfulAuthentication(authentication);

            String mainScreenId = uiProperties.getMainScreenId();
            screenBuilders.screen(this)
                    .withScreenId(mainScreenId)
                    .withOpenMode(OpenMode.ROOT)
                    .build()
                    .show();

        } catch (BadCredentialsException e) {
            notifications.create(Notifications.NotificationType.ERROR)
                    .withCaption(messages.getMessage(getClass(), "loginFailed"))
                    .withDescription(e.getMessage())
                    .show();
        }
    }

    protected void setAuthInfo(AuthInfo authInfo) {
        loginField.setValue(authInfo.getLogin());
        passwordField.setValue(authInfo.getPassword());
        rememberMeCheckBox.setValue(authInfo.getRememberMe());

        localesSelect.focus();
    }

    protected void onSuccessfulAuthentication(Authentication authentication) {
        if (cubaProperties.isLocaleSelectVisible()) {
            ClientDetails clientDetails = (ClientDetails) authentication.getDetails();
            app.addCookie(App.COOKIE_LOCALE, clientDetails.getLocale().toLanguageTag());
        }

        VaadinServletRequest request = VaadinServletRequest.getCurrent();
        VaadinServletResponse response = VaadinServletResponse.getCurrent();
        request.setAttribute(DEFAULT_PARAMETER, rememberMeCheckBox.isChecked());

        authenticationStrategy.onAuthentication(authentication, request, response);
    }
}
