import com.intellij.util.xmlb.Converter
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

val MY_DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

class LocalDateConverter : Converter<LocalDate>() {
  override fun fromString(value: String): LocalDate? {
    val trimmedValue = value.trim('_')

    return try {
      LocalDate.parse(trimmedValue, MY_DATE_FORMATTER)
    } catch (e: DateTimeParseException) {
      println("Error parsing date string: $trimmedValue")
      println(e.message)
      e.printStackTrace()
      null
    }
  }

  override fun toString(value: LocalDate): String {
    return value.format(MY_DATE_FORMATTER)
  }
}
