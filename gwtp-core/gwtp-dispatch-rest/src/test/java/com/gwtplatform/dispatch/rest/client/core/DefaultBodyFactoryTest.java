/**
 * Copyright 2015 ArcBees Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.gwtplatform.dispatch.rest.client.core;

import java.util.Date;

import javax.inject.Inject;

import org.jukito.JukitoModule;
import org.jukito.JukitoRunner;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.google.gwt.http.client.RequestBuilder;
import com.gwtplatform.dispatch.rest.client.core.parameters.HttpParameterFactory;
import com.gwtplatform.dispatch.rest.client.serialization.Serialization;
import com.gwtplatform.dispatch.rest.client.testutils.ExposedRestAction;
import com.gwtplatform.dispatch.rest.client.testutils.MockHttpParameterFactory;
import com.gwtplatform.dispatch.rest.client.testutils.SecuredRestAction;
import com.gwtplatform.dispatch.rest.client.testutils.UnsecuredRestAction;
import com.gwtplatform.dispatch.rest.shared.HttpParameter.Type;
import com.gwtplatform.dispatch.rest.shared.RestAction;
import com.gwtplatform.dispatch.shared.ActionException;

import static org.mockito.BDDMockito.given;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isNull;
import static org.mockito.Mockito.verify;

import static com.gwtplatform.dispatch.rest.client.core.MetadataType.BODY_TYPE;
import static com.gwtplatform.dispatch.rest.shared.HttpMethod.DELETE;
import static com.gwtplatform.dispatch.rest.shared.HttpMethod.GET;

@RunWith(JukitoRunner.class)
public class DefaultBodyFactoryTest {
    public static class Module extends JukitoModule {
        @Override
        protected void configureTest() {
            forceMock(RequestBuilder.class);

            bind(HttpParameterFactory.class).to(MockHttpParameterFactory.class);
        }
    }

    private static final String RELATIVE_PATH = "/rel";
    private static final String QUERY_STRING = "abc=def&ghi=jkl";

    @Inject
    private DefaultBodyFactory factory;
    @Inject
    private UriFactory uriFactory;
    @Inject
    private HttpParameterFactory parameterFactory;
    @Inject
    private RequestBuilder requestBuilder;

    @Before
    public void setUp() {
        given(uriFactory.buildQueryString(any(RestAction.class), eq(Type.FORM))).willReturn(QUERY_STRING);
    }

    @Test
    public void build_noFormParamsNoBody_setsExplicitNullAsRequestData() throws ActionException {
        // Given
        RestAction<Void> action = new UnsecuredRestAction(parameterFactory, DELETE, RELATIVE_PATH);

        // When
        factory.buildBody(requestBuilder, action);

        // Then
        verify(requestBuilder).setRequestData(isNull(String.class));
    }

    @Test
    public void requestDataShouldBeParsedFormParamsWhenActionHasFormParams() throws ActionException {
        // Given
        ExposedRestAction<Void> action = new SecuredRestAction(parameterFactory, GET, RELATIVE_PATH);
        action.addParam(Type.FORM, "Key1", "Some Value");

        // When
        factory.buildBody(requestBuilder, action);

        // Then
        verify(requestBuilder).setRequestData(eq(QUERY_STRING));
    }

    @Test
    public void requestDataShouldBeSerializedStringWhenActionHasBody(Serialization serialization,
            ActionMetadataProvider metadataProvider) throws ActionException {
        // Given
        Object unserializedObject = new Object();
        String serializationKey = "meta";
        String serializedValue = new Date().toString();

        ExposedRestAction<Void> action = new SecuredRestAction(parameterFactory, GET, RELATIVE_PATH);
        action.setBodyParam(unserializedObject);

        given(metadataProvider.getValue(action, BODY_TYPE)).willReturn(serializationKey);
        given(serialization.canSerialize(serializationKey)).willReturn(true);
        given(serialization.serialize(unserializedObject, serializationKey)).willReturn(serializedValue);

        // When
        factory.buildBody(requestBuilder, action);

        // Then
        verify(requestBuilder).setRequestData(eq(serializedValue));
    }
}
