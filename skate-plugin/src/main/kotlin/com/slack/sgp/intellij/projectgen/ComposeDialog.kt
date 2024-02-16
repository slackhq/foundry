package com.slack.sgp.intellij.projectgen

import androidx.compose.runtime.Composable
import androidx.compose.ui.awt.ComposePanel
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import java.io.File
import javax.swing.Action
import javax.swing.JComponent

abstract class ComposeDialog(val project: Project?) : DialogWrapper(project) {
    init {
        init()
    }

    override fun createCenterPanel(): JComponent {
        return ComposePanel().apply {
            setBounds(0, 0, 600, 800)
            setContent {
                dialogContent()
            }
        }
    }

    override fun doCancelAction() {
        super.doCancelAction()
        deleteProjectLock()
    }

    override fun doOKAction() {
        super.doOKAction()
        deleteProjectLock()
    }

    private fun deleteProjectLock() {
        val projectLockFile = File(project?.basePath + "/.projectgenlock")
        if (projectLockFile.exists()) {
            projectLockFile.delete()
        }
    }

    override fun createActions(): Array<Action> = emptyArray()

    @Composable
    abstract fun dialogContent()
}