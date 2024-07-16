package com.github.hspragggodaddy.insightfuldependencies.service

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import groovyjarjarantlr4.v4.runtime.misc.NotNull

interface FindUsageService {
    fun findUsages(@NotNull elements: Collection<PsiElement>, project: Project?, event: AnActionEvent?)

    companion object {
        fun getInstance(): FindUsageService {
            return ApplicationManager.getApplication().getService(FindUsageService::class.java)
        }
    }
}