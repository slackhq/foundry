package foundry.develocity

import com.gabrielfeo.develocity.api.DevelocityApi
import java.time.Instant
import okio.Path

internal interface DevelocityQuery {
  val name: String

  suspend fun run(api: DevelocityApi, outputDir: Path, since: Instant, limit: Int = Int.MAX_VALUE)
}
