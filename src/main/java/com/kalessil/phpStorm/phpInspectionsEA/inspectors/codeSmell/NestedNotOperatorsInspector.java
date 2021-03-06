package com.kalessil.phpStorm.phpInspectionsEA.inspectors.codeSmell;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.jetbrains.php.lang.lexer.PhpTokenTypes;
import com.jetbrains.php.lang.psi.PhpPsiElementFactory;
import com.jetbrains.php.lang.psi.elements.ParenthesizedExpression;
import com.jetbrains.php.lang.psi.elements.UnaryExpression;
import com.kalessil.phpStorm.phpInspectionsEA.openApi.BasePhpElementVisitor;
import com.kalessil.phpStorm.phpInspectionsEA.openApi.BasePhpInspection;
import com.kalessil.phpStorm.phpInspectionsEA.utils.ExpressionSemanticUtil;
import org.jetbrains.annotations.NotNull;

public class NestedNotOperatorsInspector extends BasePhpInspection {
    private static final String messageUseBoolCasting = "Can be replaced with (bool)%e%";
    private static final String messageUseSingleNot   = "Can be replaced with !%e%";

    @NotNull
    public String getShortName() {
        return "NestedNotOperatorsInspection";
    }

    @Override
    @NotNull
    public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
        return new BasePhpElementVisitor() {
            public void visitPhpUnaryExpression(UnaryExpression expr) {
                /* process ony not operations */
                PsiElement operator = expr.getOperation();
                if (null == operator || operator.getNode().getElementType() != PhpTokenTypes.opNOT) {
                    return;
                }

                /* process only deepest not-operator: get contained expression */
                final PsiElement value = ExpressionSemanticUtil.getExpressionTroughParenthesis(expr.getValue());
                if (null == value) {
                    return;
                }
                /* if contained expression is also inversion, do nothing -> to not report several times */
                if (value instanceof UnaryExpression) {
                    operator = ((UnaryExpression) value).getOperation();
                    if (null != operator && operator.getNode().getElementType() == PhpTokenTypes.opNOT) {
                        return;
                    }
                }

                /* check nesting level */
                PsiElement target = null;
                int nestingLevel  = 1;
                PsiElement parent = expr.getParent();
                while (parent instanceof UnaryExpression || parent instanceof ParenthesizedExpression) {
                    if (parent instanceof ParenthesizedExpression) {
                        parent = parent.getParent();
                        continue;
                    }

                    expr     = (UnaryExpression) parent;
                    operator = expr.getOperation();

                    if (null != operator && operator.getNode().getElementType() == PhpTokenTypes.opNOT) {
                        ++nestingLevel;
                        target = parent;
                    }

                    parent = expr.getParent();
                }
                if (nestingLevel == 1) {
                    return;
                }

                /* fire warning */
                final String message =
                        (nestingLevel % 2 == 0 ? messageUseBoolCasting : messageUseSingleNot)
                        .replace("%e%", value.getText());
                final LocalQuickFix fixer =
                        nestingLevel % 2 == 0 ? new UseCastingLocalFix(value) : new UseSingleNotLocalFix(value);

                holder.registerProblem(target, message, ProblemHighlightType.WEAK_WARNING, fixer);
            }
        };
    }

    private static class UseSingleNotLocalFix implements LocalQuickFix {
        final SmartPsiElementPointer<PsiElement> value;

        UseSingleNotLocalFix(@NotNull PsiElement value) {
            super();
            final SmartPointerManager factory = SmartPointerManager.getInstance(value.getProject());

            this.value = factory.createSmartPsiElementPointer(value, value.getContainingFile());
        }

        @NotNull
        @Override
        public String getName() {
            return "Use single not operator";
        }

        @NotNull
        @Override
        public String getFamilyName() {
            return getName();
        }

        @Override
        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            final PsiElement expression = descriptor.getPsiElement();
            final PsiElement value      = this.value.getElement();
            if (null != value && expression instanceof UnaryExpression) {
                //noinspection ConstantConditions I'm sure that NPE will not happen as inspection reports only finished structures
                ((UnaryExpression) expression).getValue().replace(value);
            }
        }
    }

    private static class UseCastingLocalFix implements LocalQuickFix {
        final SmartPsiElementPointer<PsiElement> value;

        UseCastingLocalFix(@NotNull PsiElement value) {
            super();
            final SmartPointerManager factory = SmartPointerManager.getInstance(value.getProject());

            this.value = factory.createSmartPsiElementPointer(value, value.getContainingFile());
        }

        @NotNull
        @Override
        public String getName() {
            return "Use boolean casting";
        }

        @NotNull
        @Override
        public String getFamilyName() {
            return getName();
        }

        @Override
        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            final PsiElement value = this.value.getElement();
            if (null != value) {
                UnaryExpression replacement = PhpPsiElementFactory.createFromText(project, UnaryExpression.class, "(bool) null");
                //noinspection ConstantConditions I'm sure that NPE will not happen as we have hardcoded expression
                replacement.getValue().replace(value);
                descriptor.getPsiElement().replace(replacement);
            }
        }
    }
}
