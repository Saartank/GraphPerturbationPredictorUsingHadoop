package AWS_utils

import org.slf4j.LoggerFactory
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import java.io.{ObjectInputStream, InputStream}
import java.io.ByteArrayInputStream
import scala.io.Source
import java.net.URI
import scala.io.BufferedSource
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.core.sync.RequestBody
import java.io.ByteArrayOutputStream
import java.io.ByteArrayOutputStream

object AWSUtils {
  private val logger = LoggerFactory.getLogger(getClass.getSimpleName)
  def getS3File(s3Path: String, region: Region = Region.US_EAST_2): BufferedSource = {
    logger.info(s"Getting file: $s3Path")
    val uri = new URI(s3Path)
    val bucket = uri.getHost
    logger.info(s"Using bucket: $bucket")
    val key = uri.getPath.drop(1) // Drop the leading slash
    logger.info(s"Using key: $key")

    val s3Client = S3Client.builder.region(region).build()
    logger.info(s"Created S3 client...")

    val request = GetObjectRequest.builder.bucket(bucket).key(key).build()
    logger.info(s"Created request...")

    val s3Object = s3Client.getObject(request)
    logger.info(s"Created S3 Object...")

    Source.fromInputStream(s3Object)
  }

  def getS3FileAsInputStream(s3Path: String, region: Region = Region.US_EAST_2):  InputStream = {
    logger.info(s"Getting file: $s3Path")
    val uri = new java.net.URI(s3Path)
    val bucket = uri.getHost
    logger.info(s"Using bucket: $bucket")
    val key = uri.getPath.drop(1) // Drop the leading slash
    logger.info(s"Using key: $key")

    val s3Client = S3Client.builder.region(region).build()
    logger.info(s"Created S3 client...")

    val request = GetObjectRequest.builder.bucket(bucket).key(key).build()
    logger.info(s"Created request...")

    val s3Object = s3Client.getObject(request)
    logger.info(s"Got S3 Object...")

    s3Object // Return InputStream
  }


  def writeS3file(os : ByteArrayOutputStream, input: ByteArrayInputStream, s3Path: String, region: Region = Region.US_EAST_2): Unit = {
    logger.info(s"Writing bytes in file: $s3Path")
    val uri = new URI(s3Path)
    val bucket = uri.getHost
    logger.info(s"Using bucket: $bucket")
    val key = uri.getPath.drop(1) // Drop the leading slash
    logger.info(s"Using key: $key")

    val s3Client = S3Client.builder.region(region).build()
    logger.info(s"Created S3 client...")

    s3Client.putObject(
      PutObjectRequest.builder()
        .bucket(bucket)
        .key(key)
        .build(),
      RequestBody.fromInputStream(input, os.size())
    )
    logger.info(s"Successfully written bytes in file : $s3Path")
    s3Client.close()
  }

  def writeS3string(data: String, s3Path: String, region: Region = Region.US_EAST_2): Unit = {
    logger.info(s"Writing string in file: $s3Path")
    val uri = new URI(s3Path)
    val bucket = uri.getHost
    logger.info(s"Using bucket: $bucket")
    val key = uri.getPath.drop(1) // Drop the leading slash
    logger.info(s"Using key: $key")

    val s3Client = S3Client.builder.region(region).build()
    logger.info(s"Created S3 client...")

    s3Client.putObject(
      PutObjectRequest.builder()
        .bucket(bucket)
        .key(key)
        .build(),
      RequestBody.fromString(data)
    )
    logger.info(s"Successfully written string in file : $s3Path")
    s3Client.close()

  }
}
