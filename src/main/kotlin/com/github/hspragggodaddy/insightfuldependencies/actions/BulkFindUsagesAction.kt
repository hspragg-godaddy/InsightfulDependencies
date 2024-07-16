package com.github.hspragggodaddy.insightfuldependencies.actions

import com.github.hspragggodaddy.insightfuldependencies.service.FindUsageService
import com.intellij.codeInsight.TargetElementUtil
import com.intellij.find.actions.FindUsagesAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.usages.PsiElementUsageTarget
import com.intellij.usages.UsageTarget
import com.intellij.usages.UsageView
import com.intellij.util.SmartList

class BulkFindUsagesAction : FindUsagesAction() {

    override fun actionPerformed(e: AnActionEvent) {
        println("Bulk Find Usages Action Performed Event")
        val project = e.getData(CommonDataKeys.PROJECT) ?: return
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        val dataContext = e.dataContext
        FindUsageService.getInstance().findUsages(getAllElements(dataContext), project, e)
    }

    private fun getAllElements(dataContext: DataContext): SmartList<PsiElement> {
        val elements = getSelectedElements(dataContext)
        val directories = SmartList<PsiDirectory>()
        elements.forEach {
            if (it is PsiDirectory) {
                directories.add(it)
            }
        }
        while (directories.isNotEmpty()) {
            val current = directories.removeLast()
            directories.addAll(current.subdirectories)
            elements.addAll(current.files)
        }
        elements.removeAll {
            it is PsiDirectory
        }
        return elements
    }

    private fun getSelectedElements(dataContext: DataContext): SmartList<PsiElement> {
        val allTargets = SmartList<PsiElement>()

        val usageTargets: Array<out UsageTarget>? = dataContext.getData(UsageView.USAGE_TARGETS_KEY)
        if (usageTargets == null) {
            val editor = dataContext.getData(CommonDataKeys.EDITOR)
            if (editor != null) {
                val offset = editor.caretModel.offset
                try {
                    val reference = TargetElementUtil.findReference(editor, offset)
                    if (reference != null) {
                        allTargets.addAll(TargetElementUtil.getInstance().getTargetCandidates(reference))
                    }
                } catch (ignore: IndexNotReadyException) {
                }
            }
        } else if (usageTargets.isNotEmpty()) {
            val target: UsageTarget = usageTargets[0]
            if (target is PsiElementUsageTarget) {
                target.element?.let {
                    allTargets += it
                }
            } else {
                println("Cannot get usage target of element: $target")
            }
        }

        return allTargets
    }
}