/**
 * Copyright 2011 ArcBees Inc.
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

package com.gwtplatform.mvp.rebind;

import java.io.PrintWriter;
import java.lang.annotation.Annotation;

import com.google.gwt.core.client.Scheduler;
import com.google.gwt.core.client.Scheduler.ScheduledCommand;
import com.google.gwt.core.ext.GeneratorContext;
import com.google.gwt.core.ext.TreeLogger;
import com.google.gwt.core.ext.UnableToCompleteException;
import com.google.gwt.core.ext.typeinfo.JClassType;
import com.google.gwt.user.rebind.ClassSourceFileComposerFactory;
import com.google.gwt.user.rebind.SourceWriter;
import com.gwtplatform.mvp.client.Bootstrapper;
import com.gwtplatform.mvp.client.DelayedBindRegistry;
import com.gwtplatform.mvp.client.PreBootstrapper;
import com.gwtplatform.mvp.client.annotations.Bootstrap;
import com.gwtplatform.mvp.client.annotations.PreBootstrap;

/**
 * Will generate a {@link com.gwtplatform.mvp.client.ApplicationController}. If the user wants his Generator to be
 * generated by GWTP, this Application controller will make sure that the Ginjector is used to trigger the initial
 * revealCurrentPlace() from the place manager.
 */
public class ApplicationControllerGenerator extends AbstractGenerator {
    private static final String HINT_URL = "https://github.com/ArcBees/GWTP/wiki/Bootstrapping-in-GWTP";
    protected static final String TOO_MANY_BOOTSTRAPPER_FOUND =
            "Too many %s have been found. Only one %s annotated with @%s must be defined. See " + HINT_URL;
    protected static final String DOES_NOT_EXTEND_BOOTSTRAPPER =
            "The %s provided doesn't implement the %s interface. See " + HINT_URL;

    private static final String DEFAULT_BOOTSTRAPPER = "com.gwtplatform.mvp.client.DefaultBootstrapper";
    protected static final String SUFFIX = "Impl";
    private static final String OVERRIDE = "@Override";
    private static final String INJECT_METHOD = "public void init() {";
    private static final String DELAYED_BIND = "%s.bind(%s.SINGLETON);";
    private static final String ONBOOTSTRAP = "%s.SINGLETON.get%s().onBootstrap();";
    private static final String ONPREBOOTSTRAP = "new %s().onPreBootstrap();";
    private static final String SCHEDULE_DEFERRED_1 = "Scheduler.get().scheduleDeferred(new ScheduledCommand() {";
    private static final String SCHEDULE_DEFERRED_2 = "public void execute() {";

    @Override
    public String generate(TreeLogger treeLogger, GeneratorContext generatorContext, String typeName)
            throws UnableToCompleteException {
        setTypeOracle(generatorContext.getTypeOracle());
        setPropertyOracle(generatorContext.getPropertyOracle());
        setTreeLogger(treeLogger);
        setTypeClass(getType(typeName));

        PrintWriter printWriter = tryCreatePrintWriter(generatorContext, SUFFIX);

        if (printWriter == null) {
            return typeName + SUFFIX;
        }

        JClassType preBootstrapper = getPreBootstrapper();

        ClassSourceFileComposerFactory composer = initComposer(preBootstrapper);
        SourceWriter sw = composer.createSourceWriter(generatorContext, printWriter);

        JClassType bootstrapper = getBootstrapper();

        String ginjectorName = new GinjectorGenerator(bootstrapper).generate(getTreeLogger(),
                generatorContext, GinjectorGenerator.DEFAULT_FQ_NAME);

        writeInit(sw, ginjectorName, preBootstrapper, bootstrapper);

        closeDefinition(sw);

        return getPackageName() + "." + getClassName();
    }

    private ClassSourceFileComposerFactory initComposer(JClassType preBootstrapper) {
        ClassSourceFileComposerFactory composer = new ClassSourceFileComposerFactory(getPackageName(), getClassName());
        composer.addImport(getTypeClass().getQualifiedSourceName());

        if (preBootstrapper != null) {
            composer.addImport(preBootstrapper.getQualifiedSourceName());
            composer.addImport(Scheduler.class.getCanonicalName());
            composer.addImport(ScheduledCommand.class.getCanonicalName());
        }

        composer.addImplementedInterface(getTypeClass().getName());

        composer.addImport(DelayedBindRegistry.class.getCanonicalName());

        return composer;
    }

    private JClassType getBootstrapper() throws UnableToCompleteException {
        return findSingleAnnotatedType(getType(DEFAULT_BOOTSTRAPPER), Bootstrapper.class, Bootstrap.class);
    }

    private JClassType getPreBootstrapper() throws UnableToCompleteException {
        return findSingleAnnotatedType(null, PreBootstrapper.class, PreBootstrap.class);
    }

    private JClassType findSingleAnnotatedType(JClassType defaultType, Class<?> clazz,
                                               Class<? extends Annotation> annotation) throws UnableToCompleteException {
        int count = 0;
        JClassType type = defaultType;
        for (JClassType t : getTypeOracle().getTypes()) {
            if (t.isAnnotationPresent(annotation)) {
                count++;

                verifyInterfaceIsImplemented(t, clazz);
                verifySingleImplementer(count, clazz, annotation);
                type = t;
            }
        }
        return type;
    }

    private void verifySingleImplementer(int count, Class<?> clazz, Class<? extends Annotation> annotation)
            throws UnableToCompleteException {
        if (count > 1) {
            getTreeLogger().log(TreeLogger.ERROR, String.format(TOO_MANY_BOOTSTRAPPER_FOUND, clazz.getSimpleName(),
                    clazz.getSimpleName(), annotation.getSimpleName()));
            throw new UnableToCompleteException();
        }
    }

    private void verifyInterfaceIsImplemented(JClassType type, Class<?> clazz) throws UnableToCompleteException {
        JClassType interfaceType = getType(clazz.getName());

        if (!type.isAssignableTo(interfaceType)) {
            getTreeLogger().log(TreeLogger.ERROR, String.format(DOES_NOT_EXTEND_BOOTSTRAPPER,
                    clazz.getSimpleName(), clazz.getSimpleName()));
            throw new UnableToCompleteException();
        }
    }

    private void writeInit(SourceWriter sw, String generatorName, JClassType preBootstrapper, JClassType bootstrapper) {
        sw.println(OVERRIDE);
        sw.println(INJECT_METHOD);
        sw.indent();

        if (preBootstrapper != null) {
            sw.println(ONPREBOOTSTRAP, preBootstrapper.getSimpleSourceName());
            sw.println();
            sw.println(SCHEDULE_DEFERRED_1);
            sw.indent();
            sw.println(OVERRIDE);
            sw.println(SCHEDULE_DEFERRED_2);
            sw.indent();
        }

        sw.println(String.format(DELAYED_BIND, DelayedBindRegistry.class.getSimpleName(), generatorName));
        sw.println();

        sw.println(String.format(ONBOOTSTRAP, generatorName, bootstrapper.getSimpleSourceName()));

        sw.outdent();
        sw.println("}");

        if (preBootstrapper != null) {
            sw.outdent();
            sw.println("});");
            sw.outdent();
            sw.println("}");
        }
    }
}
