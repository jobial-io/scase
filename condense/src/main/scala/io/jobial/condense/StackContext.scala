/*
 * Copyright (c) 2020 Jobial OÜ. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"). You may not use this file except in compliance with
 * the License. A copy of the License is located at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * or in the "license" file accompanying this file. This file is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */
package io.jobial.condense

import java.io.File
import scala.util.Try

case class StackContext(
  stackNamePrefix: String,
  stackClassName: String,
  defaultRegion: String,
  s3Bucket: String,
  s3Prefix: String,
  lambdaFile: Option[File],
  lambdaFileS3Key: Option[String],
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
        dockerImageTags.find(_._1.endsWith(image)).map(_._2)
    } yield tag

  def stackName =
    s"${stackNamePrefix}${label.map("-" + _).getOrElse("")}"
    
  lazy val stack = {
    val c = Try(Class.forName(stackClassName)).getOrElse(Class.forName(stackClassName + "$"))
    Try(c.newInstance).getOrElse(c.getField("MODULE$").get(c)).asInstanceOf[CloudformationStack]
  }
  
  def template =
    stack.template(this)
    

}
