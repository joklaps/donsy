// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.core.di

import be.kodeworks.donsy.core.backup.ContentResolverDocumentFiles
import be.kodeworks.donsy.core.backup.DocumentFiles
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class BackupModule {

    @Binds
    abstract fun bindDocumentFiles(impl: ContentResolverDocumentFiles): DocumentFiles
}
