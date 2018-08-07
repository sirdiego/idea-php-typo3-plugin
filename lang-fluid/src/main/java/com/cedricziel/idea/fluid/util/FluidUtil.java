package com.cedricziel.idea.fluid.util;

import com.cedricziel.idea.fluid.FluidViewHelperReferenceInsertHandler;
import com.cedricziel.idea.fluid.extensionPoints.NamespaceProvider;
import com.cedricziel.idea.fluid.extensionPoints.ViewHelperProvider;
import com.cedricziel.idea.fluid.lang.FluidLanguage;
import com.cedricziel.idea.fluid.lang.psi.FluidElement;
import com.cedricziel.idea.fluid.lang.psi.FluidFile;
import com.cedricziel.idea.fluid.lang.psi.FluidTypes;
import com.cedricziel.idea.fluid.lang.psi.FluidViewHelperReference;
import com.cedricziel.idea.fluid.tagMode.FluidNamespace;
import com.cedricziel.idea.fluid.variables.FluidVariable;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlAttributeValue;
import com.jetbrains.php.PhpIndex;
import com.jetbrains.php.lang.psi.elements.*;
import com.jetbrains.php.lang.psi.visitors.PhpRecursiveElementVisitor;
import gnu.trove.THashMap;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class FluidUtil {
    public static Map<String, FluidVariable> findVariablesInCurrentContext(@NotNull PsiElement element) {
        return FluidTypeResolver.collectScopeVariables(element);
    }

    public static PsiElement retrieveFluidElementAtPosition(PsiElement psiElement) {
        FileViewProvider viewProvider = psiElement.getContainingFile().getViewProvider();
        if (!viewProvider.getLanguages().contains(FluidLanguage.INSTANCE)) {
            return null;
        }

        int textOffset = psiElement.getTextOffset();
        FluidFile psi = (FluidFile) viewProvider.getPsi(FluidLanguage.INSTANCE);

        if (psiElement instanceof XmlAttributeValue) {
            textOffset += 2;
        }

        PsiElement elementAt = psi.findElementAt(textOffset);
        if (elementAt == null) {
            return null;
        }

        if (elementAt.getNode().getElementType().equals(FluidTypes.IDENTIFIER)) {
            return elementAt.getParent();
        }

        return null;
    }

    public static FluidElement retrieveFluidElementAtPosition(PsiFile psiFile, int startOffset) {
        FileViewProvider viewProvider = psiFile.getViewProvider();
        if (!viewProvider.getLanguages().contains(FluidLanguage.INSTANCE)) {
            return null;
        }

        FluidFile psi = (FluidFile) viewProvider.getPsi(FluidLanguage.INSTANCE);

        PsiElement elementAt = psi.findElementAt(startOffset);
        if (elementAt == null) {
            return null;
        }

        return (FluidElement) elementAt;
    }

    public static Map<String, FluidVariable> collectControllerVariable(FluidFile templateFile) {
        Map<String, FluidVariable> collected = new THashMap<>();
        String controllerName = null;

        VirtualFile virtualFile;
        if (templateFile.getVirtualFile() != null) {
            virtualFile = templateFile.getVirtualFile();
        } else {
            virtualFile = templateFile.getOriginalFile().getVirtualFile();
        }

        String nameWithoutExtension = virtualFile.getNameWithoutExtension();

        final String actionName = String.format("%sAction", StringUtils.uncapitalize(nameWithoutExtension));
        if (templateFile.getContainingDirectory() == null) {
            return collected;
        }

        controllerName = String.format("%sController", templateFile.getContainingDirectory().getName());

        PhpIndex phpIndex = PhpIndex.getInstance(templateFile.getProject());
        Collection<PhpClass> classesByName = phpIndex.getClassesByName(controllerName);
        classesByName.forEach(c -> {
            Method methodByName = c.findMethodByName(actionName);
            if (methodByName != null) {
                ControllerMethodWalkerVisitor visitor = new ControllerMethodWalkerVisitor();
                methodByName.accept(visitor);

                collected.putAll(visitor.variables);
            }
        });

        return collected;
    }

    public static void completeViewHelpers(@NotNull CompletionParameters parameters, @NotNull CompletionResultSet result) {
        for (NamespaceProvider extension : NamespaceProvider.EP_NAME.getExtensions()) {
            for (FluidNamespace fluidNamespace : extension.provideForElement(parameters.getPosition())) {
                for (ViewHelperProvider viewHelperProvider : ViewHelperProvider.EP_NAME.getExtensions()) {
                    viewHelperProvider
                        .provideForNamespace(parameters.getPosition().getProject(), fluidNamespace.namespace)
                        .forEach((name, vh) -> {
                            LookupElementBuilder elementBuilder;
                            if (parameters.getPosition().getParent() instanceof FluidViewHelperReference) {
                                elementBuilder = LookupElementBuilder.create(vh.name);
                            } else {
                                elementBuilder = LookupElementBuilder.create(fluidNamespace.prefix + ":" + vh.name);
                            }

                            result.addElement(
                                elementBuilder
                                    .withPresentableText(fluidNamespace.prefix + ":" + vh.name)
                                    .withIcon(vh.icon)
                                    .withTypeText(vh.fqn)
                                    .withInsertHandler(FluidViewHelperReferenceInsertHandler.INSTANCE)
                                    .withPsiElement(parameters.getPosition())
                            );
                        });
                }
            }
        }
    }

    @NotNull
    public static List<FluidNamespace> getFluidNamespaces(PsiElement elementAtCaret) {
        List<FluidNamespace> namespaces = new ArrayList<>();
        for (NamespaceProvider extension : NamespaceProvider.EP_NAME.getExtensions()) {
            namespaces.addAll(extension.provideForElement(elementAtCaret));
        }

        return namespaces;
    }

    private static class ControllerMethodWalkerVisitor extends PhpRecursiveElementVisitor {
        private Map<String, FluidVariable> variables = new THashMap<>();

        @Override
        public void visitPhpMethodReference(MethodReference reference) {
            String name = reference.getName();
            if (!name.equals("assign") && !name.equals("assignMultiple")) {
                super.visitPhpMethodReference(reference);
                return;
            }

            if (name.equals("assign") && reference.getParameterList() != null) {
                PsiElement[] parameters = reference.getParameterList().getParameters();
                if (parameters.length < 2) {
                    super.visitPhpMethodReference(reference);
                    return;
                }

                if (!(parameters[0] instanceof StringLiteralExpression)) {
                    super.visitPhpMethodReference(reference);
                    return;
                }

                String variableName = ((StringLiteralExpression) parameters[0]).getContents();
                Set<String> variableTypes = new HashSet<>();
                if (parameters[1] instanceof PhpTypedElement) {
                    variableTypes.addAll(((PhpTypedElement) parameters[1]).getType().getTypes());
                }

                variables.put(variableName, new FluidVariable(variableTypes, parameters[1]));
            }

            if (name.equals("assignMultiple") && reference.getParameterList() != null && reference.getParameterList().getParameters().length == 1) {
                PsiElement parameter = reference.getParameterList().getParameters()[0];
                if (parameter instanceof ArrayCreationExpression) {
                    ((ArrayCreationExpression) parameter).getHashElements().forEach(he -> {
                        if (he.getKey() instanceof StringLiteralExpression) {
                            String key = ((StringLiteralExpression) he.getKey()).getContents();
                            Set<String> variableTypes = new HashSet<>();
                            if (he.getValue() instanceof PhpTypedElement) {
                                variableTypes.addAll(((PhpTypedElement) he.getValue()).getType().getTypes());
                            }

                            variables.put(key, new FluidVariable(variableTypes, he.getValue()));
                        }
                    });
                }
            }

            super.visitPhpMethodReference(reference);
        }
    }
}
