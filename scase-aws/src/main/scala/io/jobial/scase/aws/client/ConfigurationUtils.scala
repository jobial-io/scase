package io.jobial.scase.aws.client

import com.amazonaws.regions.DefaultAwsRegionProviderChain

import scala.util.Try

trait ConfigurationUtils {
  
  def getDefaultRegion = 
    Try(new DefaultAwsRegionProviderChain().getRegion).toEither
}
