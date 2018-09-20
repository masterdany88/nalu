/*
 * Copyright (c) 2018 - Frank Hossfeld
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not
 *  use this file except in compliance with the License. You may obtain a copy of
 *  the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *  WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *  License for the specific language governing permissions and limitations under
 *  the License.
 *
 */

package com.github.nalukit.nalu.processor.scanner;

import com.github.nalukit.nalu.client.component.AbstractSplitterController;
import com.github.nalukit.nalu.client.component.annotation.AcceptParameter;
import com.github.nalukit.nalu.client.component.annotation.SplitterController;
import com.github.nalukit.nalu.processor.ProcessorException;
import com.github.nalukit.nalu.processor.ProcessorUtils;
import com.github.nalukit.nalu.processor.model.ApplicationMetaModel;
import com.github.nalukit.nalu.processor.model.intern.ClassNameModel;
import com.github.nalukit.nalu.processor.model.intern.ControllerModel;
import com.github.nalukit.nalu.processor.model.intern.ParameterAcceptor;
import com.github.nalukit.nalu.processor.model.intern.SplitterModel;
import com.github.nalukit.nalu.processor.scanner.validation.AcceptParameterAnnotationValidator;
import com.github.nalukit.nalu.processor.scanner.validation.SplitterControllerAnnotationValidator;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.*;
import javax.lang.model.util.SimpleTypeVisitor6;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class SplitterControllerAnnotationScanner {

  private ProcessorUtils processorUtils;

  private ProcessingEnvironment processingEnvironment;

  private ApplicationMetaModel applicationMetaModel;

  @SuppressWarnings("unused")
  private SplitterControllerAnnotationScanner(Builder builder) {
    super();
    this.processingEnvironment = builder.processingEnvironment;
    this.applicationMetaModel = builder.applicationMetaModel;
    setUp();
  }

  private void setUp() {
    this.processorUtils = ProcessorUtils.builder()
                                        .processingEnvironment(this.processingEnvironment)
                                        .build();
  }

  public static Builder builder() {
    return new Builder();
  }

  ApplicationMetaModel scan(RoundEnvironment roundEnvironment)
    throws ProcessorException {
    // handle SplitterController-annotation
    for (Element element : roundEnvironment.getElementsAnnotatedWith(SplitterController.class)) {
      // handle splitter
      SplitterModel splitterModel = handleSplitter(roundEnvironment,
                                                   element);
      // handle AcceptParameter annotation
      handleAcceptParameters(roundEnvironment,
                             element,
                             splitterModel);
      // add model to configuration ...
      this.applicationMetaModel.getSplitters()
                               .add(splitterModel);
    }
    return this.applicationMetaModel;
  }

  private SplitterModel handleSplitter(RoundEnvironment roundEnvironment,
                                       Element element)
    throws ProcessorException {
    // do validation
    SplitterControllerAnnotationValidator.builder()
                                         .roundEnvironment(roundEnvironment)
                                         .processingEnvironment(processingEnvironment)
                                         .splitterElement(element)
                                         .build()
                                         .validate();
    // get Annotation ...
    SplitterController annotation = element.getAnnotation(SplitterController.class);
    // handle ...
    TypeElement componentTypeElement = this.getComponentTypeElement(annotation);
    if (componentTypeElement == null) {
      throw new ProcessorException("Nalu-Processor: componentTypeElement is null");
    }
    TypeElement componentInterfaceTypeElement = this.getComponentInterfaceTypeElement(annotation);
    TypeMirror componentTypeTypeMirror = this.getComponentType(element.asType());
    // check and save the component type ...
    if (applicationMetaModel.getComponentType() == null) {
      applicationMetaModel.setComponentType(new ClassNameModel(componentTypeTypeMirror.toString()));
    } else {
      ClassNameModel compareValue = new ClassNameModel(componentTypeTypeMirror.toString());
      if (!applicationMetaModel.getComponentType()
                               .equals(compareValue)) {
        throw new ProcessorException("Nalu-Processor: componentType >>" + compareValue + "<< is different. All controllers must implement the componentType!");
      }
    }
    // create model ...
    return new SplitterModel(new ClassNameModel(element.toString()),
                             new ClassNameModel(componentInterfaceTypeElement.toString()),
                             new ClassNameModel(componentTypeElement.toString()));
  }

  private void handleAcceptParameters(RoundEnvironment roundEnvironment,
                                      Element element,
                                      SplitterModel splitterModel)
    throws ProcessorException {
    TypeElement typeElement = (TypeElement) element;
    List<Element> annotatedElements = this.processorUtils.getMethodFromTypeElementAnnotatedWith(this.processingEnvironment,
                                                                                                typeElement,
                                                                                                AcceptParameter.class);
    // get all controllers, that use the splitter (for validation)
    for (ControllerModel model : this.getControllerUsingSplitter(element)) {
      // validate
      AcceptParameterAnnotationValidator.builder()
                                        .roundEnvironment(roundEnvironment)
                                        .processingEnvironment(processingEnvironment)
                                        .controllerModel(model)
                                        .listOfAnnotatedElements(annotatedElements)
                                        .build()
                                        .validate();
    }
    // add to ControllerModel ...
    for (Element annotatedElement : annotatedElements) {
      ExecutableElement executableElement = (ExecutableElement) annotatedElement;
      AcceptParameter annotation = executableElement.getAnnotation(AcceptParameter.class);
      splitterModel.getParameterAcceptors()
                     .add(new ParameterAcceptor(annotation.value(),
                                                executableElement.getSimpleName()
                                                                 .toString()));
    }
  }

  private TypeElement getComponentTypeElement(SplitterController annotation) {
    try {
      annotation.component();
    } catch (MirroredTypeException exception) {
      return (TypeElement) this.processingEnvironment.getTypeUtils()
                                                     .asElement(exception.getTypeMirror());
    }
    return null;
  }

  private TypeElement getComponentInterfaceTypeElement(SplitterController annotation) {
    try {
      annotation.componentInterface();
    } catch (MirroredTypeException exception) {
      return (TypeElement) this.processingEnvironment.getTypeUtils()
                                                     .asElement(exception.getTypeMirror());
    }
    return null;
  }

  private TypeMirror getComponentType(final TypeMirror typeMirror) {
    final TypeMirror[] result = {null};
    TypeMirror type = this.processorUtils.getFlattenedSupertype(this.processingEnvironment.getTypeUtils(),
                                                                typeMirror,
                                                                this.processorUtils.getElements()
                                                                                   .getTypeElement(AbstractSplitterController.class.getCanonicalName())
                                                                                   .asType());
    if (type == null) {
      return result[0];
    }
    type.accept(new SimpleTypeVisitor6<Void, Void>() {
                  @Override
                  protected Void defaultAction(TypeMirror typeMirror,
                                               Void v) {
                    throw new UnsupportedOperationException();
                  }

                  @Override
                  public Void visitPrimitive(PrimitiveType primitiveType,
                                             Void v) {
                    return null;
                  }

                  @Override
                  public Void visitArray(ArrayType arrayType,
                                         Void v) {
                    return null;
                  }

                  @Override
                  public Void visitDeclared(DeclaredType declaredType,
                                            Void v) {
                    List<? extends TypeMirror> typeArguments = declaredType.getTypeArguments();
                    if (!typeArguments.isEmpty()) {
                      if (typeArguments.size() == 3) {
                        result[0] = typeArguments.get(2);
                      }
                    }
                    return null;
                  }

                  @Override
                  public Void visitError(ErrorType errorType,
                                         Void v) {
                    return null;
                  }

                  @Override
                  public Void visitTypeVariable(TypeVariable typeVariable,
                                                Void v) {
                    return null;
                  }
                },
                null);
    return result[0];
  }

  private List<ControllerModel> getControllerUsingSplitter(Element element) {
    List<ControllerModel> models = new ArrayList<>();
    this.applicationMetaModel.getController()
                             .forEach(controllerModel -> {
                               controllerModel.getSplitters()
                                              .stream()
                                              .filter(controllerSplitterModel -> element.toString()
                                                                                        .equals(controllerSplitterModel.getSplitter()
                                                                                                                       .getClassName()))
                                              .map(controllerSplitterModel -> controllerModel)
                                              .collect(Collectors.toList())
                                              .forEach(models::add);
                             });
    return models;
  }

  public static class Builder {

    ProcessingEnvironment processingEnvironment;

    ApplicationMetaModel applicationMetaModel;

    public Builder processingEnvironment(ProcessingEnvironment processingEnvironment) {
      this.processingEnvironment = processingEnvironment;
      return this;
    }

    public Builder applicationMetaModel(ApplicationMetaModel applicationMetaModel) {
      this.applicationMetaModel = applicationMetaModel;
      return this;
    }

    public SplitterControllerAnnotationScanner build() {
      return new SplitterControllerAnnotationScanner(this);
    }
  }
}
