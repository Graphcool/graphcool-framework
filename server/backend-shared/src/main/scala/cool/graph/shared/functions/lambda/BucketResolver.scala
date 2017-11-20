package cool.graph.shared.functions.lambda

import com.amazonaws.auth.{AWSStaticCredentialsProvider, BasicAWSCredentials}
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration
import com.amazonaws.services.s3.{AmazonS3, AmazonS3ClientBuilder}
import cool.graph.shared.models.Project
import software.amazon.awssdk.regions.Region

trait BucketResolver {
  def bucketNameForProject(project: Project): String
  def s3ClientForProject(project: Project): AmazonS3
}

case class MultiRegionBucketResolver(accessKeyId: String, secretAccessKey: String) extends BucketResolver {
  val s3Credentials = new BasicAWSCredentials(accessKeyId, secretAccessKey)

  val deployBuckets = Map(
    cool.graph.shared.models.Region.EU_WEST_1      -> "graphcool-lambda-deploy-eu-west-1",
    cool.graph.shared.models.Region.US_WEST_2      -> "graphcool-lambda-deploy-us-west-2",
    cool.graph.shared.models.Region.AP_NORTHEAST_1 -> "graphcool-lambda-deploy-ap-northeast-1"
  )

  override def bucketNameForProject(project: Project): String = deployBuckets(project.region)

  override def s3ClientForProject(project: Project): AmazonS3 = {
    val region = Region.of(project.region.toString).toString

    AmazonS3ClientBuilder.standard
      .withCredentials(new AWSStaticCredentialsProvider(s3Credentials))
      .withEndpointConfiguration(new EndpointConfiguration(s"s3-$region.amazonaws.com", region))
      .build
  }
}

case class SingleRegionBucketResolver(accessKeyId: String, secretAccessKey: String, awsRegion: String) extends BucketResolver {
  val s3Credentials = new BasicAWSCredentials(accessKeyId, secretAccessKey)
  val deployBucket  = sys.env.getOrElse("LAMBDA_DEPLOY_BUCKET", sys.error("Env var LAMBDA_DEPLOY_BUCKET required but not found"))

  val s3Client = AmazonS3ClientBuilder.standard
    .withCredentials(new AWSStaticCredentialsProvider(s3Credentials))
    .withEndpointConfiguration(new EndpointConfiguration(s"s3-$awsRegion.amazonaws.com", awsRegion.toString))
    .build

  // All projects deployed to a cluster with a SingleRegionBucketResolver are automatically in that region, regardless of what the project config states.
  override def bucketNameForProject(project: Project): String = deployBucket
  override def s3ClientForProject(project: Project): AmazonS3 = s3Client
}
