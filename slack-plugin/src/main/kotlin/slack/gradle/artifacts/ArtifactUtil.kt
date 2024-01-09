package slack.gradle.artifacts

import org.gradle.api.Project
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.Usage

internal fun AttributeContainer.addCommonAttributes(project: Project, category: String) {
  attribute(Category.CATEGORY_ATTRIBUTE, project.objects.named(Category::class.java, category))
  attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(Usage::class.java, "sgp"))
}
