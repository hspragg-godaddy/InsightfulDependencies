package com.github.hspragggodaddy.insightfuldependencies.service

import com.github.hspragggodaddy.insightfuldependencies.BulkFindUsagesExporter
import com.intellij.find.FindManager
import com.intellij.find.findUsages.FindUsagesHandler
import com.intellij.find.findUsages.FindUsagesHandlerFactory
import com.intellij.find.findUsages.FindUsagesManager
import com.intellij.find.impl.FindInProjectUtil
import com.intellij.ide.util.ExportToFileUtil
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.usages.Usage
import com.intellij.usages.UsageTarget
import com.intellij.usages.UsageTargetUtil
import com.intellij.usages.UsageViewManager
import com.intellij.util.concurrency.AppExecutorUtil
import com.squareup.wire.internal.newMutableList
import java.util.concurrent.Callable
import java.util.concurrent.atomic.AtomicInteger

@Service
class FindUsageServiceImpl : FindUsageService {
    override fun findUsages(elements: Collection<PsiElement>, project: Project?, event: AnActionEvent?) {
        val e = event ?: return
        if (project == null) return
        val allUsages = newMutableList<Pair<Array<UsageTarget>, Array<Usage>>>()
        val classes = elements.filter {
            it.javaClass.name.contains("PsiJavaFile")
        }
            .map {
                it.javaClass.getMethod("getClasses").invoke(it) as Array<PsiElement>
            }.flatMap {
                it.toList()
            }
        val atomicClassCount = AtomicInteger(0)
        val outputUsages = newMutableList<String>()

        classes.forEach { element ->
            ReadAction.nonBlocking(
                Callable {
                    findUsages(element, project, allUsages)
                }
            )
                .inSmartMode(project)
                .finishOnUiThread(
                    ModalityState.nonModal()
                ) { usagePairs ->
                    println("Finished finding usages of $element (${atomicClassCount.incrementAndGet()}/${classes.size})")
                    outputUsages.add(createUsageView(project, usagePairs.first, usagePairs.second))
                    if (atomicClassCount.get() == classes.size) {
                        println("All classes have been processed")
                        ExportToFileUtil.chooseFileAndExport(project, BulkFindUsagesExporter(outputUsages, project))
                    }
                }
                .submit(AppExecutorUtil.getAppExecutorService())
        }
    }

    private fun findUsages(
        element: PsiElement,
        project: Project,
        usageList: MutableList<Pair<Array<UsageTarget>, Array<Usage>>>
    ): Pair<Array<UsageTarget>, Array<Usage>> {
        val handlers = getAllFindUsageHandlers(element, project)
        val usages = mutableListOf<Usage>()
        handlers.forEach { handler ->
            handler.findUsagesOptions.searchScope = GlobalSearchScope.everythingScope(project)
            val usageSearcher = FindUsagesManager.createUsageSearcher(
                handler,
                handler.primaryElements,
                handler.secondaryElements,
                handler.findUsagesOptions
            )
            usageSearcher.generate {
                usages.add(it)
                true
            }
        }
        val result = Pair(UsageTargetUtil.findUsageTargets(element), usages.toTypedArray())
        usageList.add(result)
        return result
    }

    fun getAllFindUsageHandlers(element: PsiElement, project: Project): List<FindUsagesHandler> {
        val handlers = mutableListOf<FindUsagesHandler>()
        for (factory in FindUsagesHandlerFactory.EP_NAME.getExtensions(project)) {
            if (factory.canFindUsages(element)) {
                val handler: FindUsagesHandler =
                    factory.createFindUsagesHandler(element, FindUsagesHandlerFactory.OperationMode.DEFAULT)
                if (handler === FindUsagesHandler.NULL_HANDLER) continue
                handlers.add(handler)
            }
        }
        return handlers
    }

    private fun createUsageView(project: Project, usageTargets: Array<UsageTarget>, usages: Array<Usage>): String {
        val findModel = FindManager.getInstance(project).findInProjectModel.clone()
        val presentation = FindInProjectUtil.setupViewPresentation(findModel)
        val view =
            UsageViewManager.getInstance(project)
                .showUsages(usageTargets, usages, presentation)
        if (view.component !is DataProvider) return ""
        val dataProvider = view.component as DataProvider
        val exporter = PlatformDataKeys.EXPORTER_TO_TEXT_FILE.getData(dataProvider)
        if (exporter == null) {
            println("Exporter is null")
            return ""
        }
        while (!exporter.canExport()) {
            println("Exporter is not ready...")
            Thread.sleep(100)
        }
        return exporter.reportText
    }


}