package io.jobial.scase.cloudformation


case class StackContext(
  stackName: String,
  label: Option[String],
  dockerImageTags: Option[Map[String, String]],
  printOnly: Boolean,
  update: Boolean = false,
  attributes: Map[String, String] = Map()
) {

  def tagForImage(image: String) =
    for {
      dockerImageTags <- dockerImageTags
      tag: String <- dockerImageTags.find(_._1.endsWith(s"/$image")).map(_._2) orElse
        dockerImageTags.find(_._1.endsWith(s"cloudtemp/$image")).map(_._2) orElse
        dockerImageTags.find(_._1.endsWith(image)).map(_._2)
    } yield tag
}
