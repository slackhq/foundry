package slack.gradle.artifacts

import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConsumableConfiguration
import org.gradle.api.artifacts.DependencyScopeConfiguration
import org.gradle.api.artifacts.ResolvableConfiguration
import org.gradle.api.attributes.Usage

/**
 * Helper APIs for creating consumable and resolvable configurations.
 */
internal object ConfigurationManager {
    fun maybeCreateConsumable(
        project: Project,
        name: String,
        description: String,
        usage: String,
    ): NamedDomainObjectProvider<ConsumableConfiguration> {
        return project.configurations.consumable(name) {
            isVisible = false
            this.description = description
            outgoing.artifact(Usage.USAGE_ATTRIBUTE) {
                attributes {
                    attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(Usage::class.java, usage))
                }
            }
        }
    }

    fun maybeCreateResolvable(
        project: Project,
        name: String,
        description: String,
        usage: String,
    ): Pair<NamedDomainObjectProvider<DependencyScopeConfiguration>, NamedDomainObjectProvider<ResolvableConfiguration>> {
        val commonConfig: Configuration.() -> Unit = {
            isVisible = false
            this.description = description
            outgoing.artifact(Usage.USAGE_ATTRIBUTE) {
                attributes {
                    attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(Usage::class.java, usage))
                }
            }
        }
        val dependencyScope = project.configurations.dependencyScope("${name}Dependencies") {
            commonConfig()
        }
        val resolvable = project.configurations.resolvable(name) {
            extendsFrom(dependencyScope.get())
            commonConfig()
        }
        return dependencyScope to resolvable
    }
}