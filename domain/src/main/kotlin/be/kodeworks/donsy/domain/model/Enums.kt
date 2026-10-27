// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.domain.model

// Stored in the database and in exports by constant name: never rename a constant,
// only add new ones (see docs/spec.md).

enum class TaskStatus { ACTIVE, PAUSED, DONE, ARCHIVED }

enum class Importance { NORMAL, HIGH }

enum class Effort { LOW, MEDIUM, HIGH }

enum class BrainDumpStatus { INBOX, CONVERTED }

enum class FocusSessionStatus { RUNNING, PAUSED, COMPLETED, ENDED_EARLY, CANCELLED }

/** How much energy the user says they have right now (optional check-in). */
enum class EnergyLevel { LOW, MEDIUM, HIGH }
