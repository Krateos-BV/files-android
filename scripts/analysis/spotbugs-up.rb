## Script originally from https://github.com/tir38/android-lint-entropy-reducer at 07.05.2017
# heavily modified since then

# SPDX-FileCopyrightText: 2017-2024 Nextcloud GmbH and Nextcloud contributors
# SPDX-FileCopyrightText: 2017 Jason Atwood 
# SPDX-FileCopyrightText: 2017 Tobias Kaminsky <tobias@kaminsky.me>
# SPDX-License-Identifier: AGPL-3.0-or-later OR GPL-2.0-only

Encoding.default_external = Encoding::UTF_8
Encoding.default_internal = Encoding::UTF_8

puts "=================== starting Android Spotbugs Entropy Reducer ===================="

# get args
baseline_xml = ARGV[0]

require 'fileutils'
require 'pathname'
require 'open3'

# run Spotbugs
puts "running Spotbugs..."
spotbugs_ran = system './gradlew spotbugsGplayDebug'

# a failed build leaves no report, which would otherwise count as 0 warnings
if !spotbugs_ran || !File.size?("app/build/reports/spotbugs/gplayDebug.xml")
    puts "FAIL: Spotbugs did not produce a report"
    exit 3
end

# find number of warnings
current_warning_count = `./scripts/analysis/spotbugsSummary.py --total`.to_i
puts "found warnings: " + current_warning_count.to_s

# get warning counts from the committed baseline
previous_xml = baseline_xml
previous_results = File.file?(previous_xml)

if !previous_results
    puts "FAIL: baseline #{previous_xml} not found"
    exit 3
end

if previous_results == true
    previous_warning_count = `./scripts/analysis/spotbugsSummary.py --total --file #{previous_xml}`.to_i
    puts "previous warnings: " + previous_warning_count.to_s
end

# compare previous warning count with current warning count
if previous_results == true && current_warning_count > previous_warning_count
    puts "FAIL: warning count increased"
    exit 1
end

# check if warning and error count stayed the same
if  previous_results == true && current_warning_count == previous_warning_count
    puts "SUCCESS: count stayed the same"
    exit 0
end

# warning count DECREASED
if previous_results == true && current_warning_count < previous_warning_count
    puts "SUCCESS: count decreased from " + previous_warning_count.to_s + " to " + current_warning_count.to_s
end
