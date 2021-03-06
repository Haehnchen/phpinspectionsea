package com.kalessil.phpStorm.phpInspectionsEA.inspectors.semanticalAnalysis;


import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.php.lang.psi.elements.Method;
import com.jetbrains.php.lang.psi.elements.PhpClass;
import com.jetbrains.php.lang.psi.elements.PhpModifier;
import com.kalessil.phpStorm.phpInspectionsEA.openApi.BasePhpElementVisitor;
import com.kalessil.phpStorm.phpInspectionsEA.openApi.BasePhpInspection;
import org.jetbrains.annotations.NotNull;

public class SingletonFactoryPatternViolationInspector extends BasePhpInspection {
    private static final String strProblemDescription             = "Ensure that one of public getInstance/create* methods are defined";
    private static final String strProblemConstructorNotProtected = "Singleton constructor should be protected";

    @NotNull
    public String getShortName() {
        return "SingletonFactoryPatternViolationInspection";
    }

    @Override
    @NotNull
    public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
        return new BasePhpElementVisitor() {
            public void visitPhpClass(PhpClass clazz) {
                final Method constructor = clazz.getOwnConstructor();
                if (null == constructor || null == clazz.getNameIdentifier()) {
                    return;
                }
                final PhpModifier.Access constructorAccessModifiers = constructor.getAccess();

                final Method getInstance     = clazz.findOwnMethodByName("getInstance");
                final boolean hasGetInstance = (null != getInstance && getInstance.getAccess().isPublic());
                if (hasGetInstance) {
                    if (constructorAccessModifiers.isPublic()){
                        /* private ones already covered with other inspections */
                        holder.registerProblem(clazz.getNameIdentifier(), strProblemConstructorNotProtected, ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
                    }

                    return;
                }

                /* ignore private / public constructors in factories */
                if (!constructorAccessModifiers.isProtected()) {
                    return;
                }
                for (Method ownMethod: clazz.getOwnMethods()) {
                    final String methodName = ownMethod.getName();
                    if (methodName.startsWith("create") || methodName.startsWith("from")) {
                        return;
                    }
                }

                holder.registerProblem(clazz.getNameIdentifier(), strProblemDescription, ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
            }
        };
    }
}
