/*
 * Copyright 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nebula.plugin.bintray

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.gradle.api.GradleException
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

class BintrayClient private constructor(val bintrayService: BintrayService) {

    data class Builder(
        var user: String? = null,
        var apiKey: String? = null,
        var apiUrl: String? = null) {

        fun user(user: String) = apply { this.user = user }
        fun apiKey(apiKey: String) = apply { this.apiKey = apiKey }
        fun apiUrl(apiUrl: String) = apply { this.apiUrl = apiUrl }
        fun build() = BintrayClient(bintray(apiUrl!!, user!!, apiKey!!))
    }

    fun createOrUpdatePackage(subject: String, repo: String, pkg: String, packageRequest: PackageRequest) {
        val getPackageResult = bintrayService.getPackage(subject, repo, pkg).execute()
        if(!getPackageResult.isSuccessful && getPackageResult.code() != 404) {
            throw GradleException("Could not obtain information for package $repo/$subject/$pkg - ${getPackageResult.errorBody()?.string()}")
        }

        val createOrUpdatePackageResult = if(getPackageResult.isSuccessful)  bintrayService.updatePackage(subject, repo, packageRequest).execute() else bintrayService.createPackage(subject, repo, packageRequest).execute()
        if(!createOrUpdatePackageResult.isSuccessful) {
            throw GradleException("Could not create or update information for package $repo/$subject/$pkg - ${getPackageResult.errorBody()?.string()}")
        }
    }

    fun publishVersion(subject: String, repo: String, pkg: String, version: String, publishRequest: PublishRequest) {
        val publishVersionResult = bintrayService.publishVersion(subject, repo, pkg, version, publishRequest).execute()
        if(!publishVersionResult.isSuccessful) {
            throw GradleException("Could not publish $version version for package $repo/$subject/$pkg - ${publishVersionResult.errorBody()?.string()}")
        }
    }
}

fun bintray(apiUrl: String, user: String, apiKey: String): BintrayService = Retrofit.Builder()
        .baseUrl(apiUrl)
        .client(OkHttpClient.Builder()
                .addInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BASIC))
                .addInterceptor({ chain ->
                    chain.proceed(chain.request().newBuilder()
                            .header("Authorization", Credentials.basic(user, apiKey))
                            .build())
                })
                .build())
        .addConverterFactory(MoshiConverterFactory.create(Moshi.Builder().add(KotlinJsonAdapterFactory()).build()))
        .build()
        .create(BintrayService::class.java)