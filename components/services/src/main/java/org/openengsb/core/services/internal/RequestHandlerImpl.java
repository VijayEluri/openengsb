/**
 * Licensed to the Austrian Association for Software Tool Integration (AASTI)
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. The AASTI licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.openengsb.core.services.internal;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.ClassUtils;
import org.apache.commons.lang.reflect.MethodUtils;
import org.openengsb.core.api.OsgiUtilsService;
import org.openengsb.core.api.context.ContextHolder;
import org.openengsb.core.api.remote.CustomJsonMarshaller;
import org.openengsb.core.api.remote.CustomMarshallerRealTypeAccess;
import org.openengsb.core.api.remote.MethodCall;
import org.openengsb.core.api.remote.MethodResult;
import org.openengsb.core.api.remote.MethodResult.ReturnType;
import org.openengsb.core.api.remote.RequestHandler;
import org.openengsb.core.api.remote.UseCustomJasonMarshaller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Throwables;

public class RequestHandlerImpl implements RequestHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(RequestHandlerImpl.class);
    private OsgiUtilsService utilsService;

    @Override
    public MethodResult handleCall(MethodCall call) {
        Map<String, String> metaData = call.getMetaData();
        String contextId = metaData.get("contextId");
        if (contextId != null) {
            ContextHolder.get().setCurrentContextId(contextId);
        }
        Object service = retrieveOpenEngSBService(call);
        Method method = findMethod(service, call.getMethodName(), getArgTypes(call));
        Object[] args = retrieveArguments(call, method);
        MethodResult methodResult = invokeMethod(service, method, args);
        methodResult.setMetaData(call.getMetaData());
        return methodResult;
    }

    private Object[] retrieveArguments(MethodCall call, Method method) {
        Annotation[][] parameterAnnotations = method.getParameterAnnotations();
        Object[] originalArgs = call.getArgs();
        for (int i = 0; i < originalArgs.length; i++) {
            Annotation[] currentArgAnnotations = parameterAnnotations[i];
            Class<? extends CustomJsonMarshaller<?>> transformationAnnotation =
                searchForTransformationAnnotation(currentArgAnnotations);
            if (transformationAnnotation == null) {
                continue;
            }
            CustomJsonMarshaller<?> transformationInstance = createTransformationInstance(transformationAnnotation);
            originalArgs[i] = transformationInstance.transformArg(originalArgs[i]);
        }
        return originalArgs;
    }

    private CustomJsonMarshaller<?> createTransformationInstance(
            Class<? extends CustomJsonMarshaller<?>> transformationAnnotation) {
        try {
            return transformationAnnotation.newInstance();
        } catch (Exception e) {
            throw new IllegalStateException("It's not possible to create transformation because of "
                    + Throwables.getStackTraceAsString(e));
        }
    }

    private Class<? extends CustomJsonMarshaller<?>> searchForTransformationAnnotation(
            Annotation[] currentArgAnnotations) {
        for (Annotation annotation : currentArgAnnotations) {
            if (annotation instanceof UseCustomJasonMarshaller) {
                return ((UseCustomJasonMarshaller) annotation).value();
            }
        }
        return null;
    }

    private Object retrieveOpenEngSBService(MethodCall call) {
        Map<String, String> metaData = call.getMetaData();
        String serviceId = metaData.get("serviceId");
        String filter = metaData.get("serviceFilter");
        String filterString = createFilterString(filter, serviceId);
        return utilsService.getService(filterString);
    }

    private String createFilterString(String filter, String serviceId) {
        if (filter == null) {
            if (serviceId == null) {
                throw new IllegalArgumentException("must specify either filter or serviceId");
            }
            return String.format("(%s=%s)", org.osgi.framework.Constants.SERVICE_PID, serviceId);
        } else {
            if (serviceId == null) {
                return filter;
            }
            return String.format("(&%s(%s=%s))", filter, org.osgi.framework.Constants.SERVICE_PID, serviceId);
        }
    }

    private MethodResult invokeMethod(Object service, Method method, Object[] args) {
        MethodResult returnTemplate = new MethodResult();
        try {
            Object result = method.invoke(service, args);
            if (method.getReturnType().getName().equals("void")) {
                returnTemplate.setType(ReturnType.Void);
            } else {
                returnTemplate.setType(ReturnType.Object);
                returnTemplate.setArg(result);
                String resultClassName =
                    result == null ? method.getReturnType().getName() : result.getClass().getName();
                returnTemplate.setClassName(resultClassName);
            }
        } catch (Exception e) {
            LOGGER.warn("Exception in remote method invocation: ", e);
            returnTemplate.setType(ReturnType.Exception);
            if (e.getClass().equals(InvocationTargetException.class)) {
                e = (Exception) e.getCause();
                // if it's not an Exception we are in REAL trouble anyway
            }
            returnTemplate.setArg(e.getCause());
            returnTemplate.setClassName(e.getClass().getName());
        }
        return returnTemplate;
    }

    private Method findMethod(Object service, String methodName, Class<?>[] argTypes) {
        Method method;

        Class<?> serviceClass = retrieveRealServiceClass(service);
        if (serviceClass.isInstance(CustomMarshallerRealTypeAccess.class)) {
            serviceClass = ((CustomMarshallerRealTypeAccess) service).getRealUnproxiedType();
        }
        method = MethodUtils.getMatchingAccessibleMethod(serviceClass, methodName, argTypes);
        if (method == null) {
            throw new IllegalArgumentException(String.format("could not find method matching arguments \"%s(%s)\"",
                methodName, ArrayUtils.toString(argTypes)));
        }

        return method;
    }

    /**
     * TODO: OPENENGSB-1976
     * 
     * This is a workaround for the mess with Aries JPA proxies.
     */
    private Class<?> retrieveRealServiceClass(Object service) {
        Class<?> serviceClass = service.getClass();
        Method realTypeMethod = null;
        try {
            realTypeMethod = serviceClass.getMethod("getRealUnproxiedType");
        } catch (NoSuchMethodException e) {
            // no problem this method does not have to exist
        }
        if (realTypeMethod != null) {
            try {
                serviceClass = (Class<?>) realTypeMethod.invoke(service);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return serviceClass;
    }

    private Class<?>[] getArgTypes(MethodCall args) {
        List<Class<?>> clazzes = new ArrayList<Class<?>>();
        for (String clazz : args.getClasses()) {
            try {
                clazzes.add(ClassUtils.getClass(this.getClass().getClassLoader(), clazz));
            } catch (ClassNotFoundException e) {
                throw new IllegalArgumentException("The classes defined could not be found", e);
            }
        }
        return clazzes.toArray(new Class<?>[0]);
    }

    public void setUtilsService(OsgiUtilsService utilsService) {
        this.utilsService = utilsService;
    }

}
