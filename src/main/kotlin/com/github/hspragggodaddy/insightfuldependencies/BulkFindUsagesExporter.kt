package com.github.hspragggodaddy.insightfuldependencies

import com.intellij.ide.ExporterToTextFile
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VirtualFile
import java.io.File

class BulkFindUsagesExporter(
    private var reports: List<String> = emptyList(),
    private val project: Project
) : ExporterToTextFile {

    override fun getReportText(): String {
        return reports.joinToString("\n")
    }

    override fun getDefaultFilePath(): String {
        val baseDir: VirtualFile = this.project.guessProjectDir() ?: return "output.txt"
        return baseDir.presentableUrl + File.separator + "output.txt"
    }

    override fun canExport(): Boolean {
        return true
    }
}