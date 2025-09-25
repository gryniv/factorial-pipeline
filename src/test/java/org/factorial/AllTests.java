package org.factorial;

import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.IncludeEngines;

@Suite
@IncludeEngines("junit-jupiter")
@SelectPackages("org.factorial")   // підхопить factorial.* (без цього класу, бо інший пакет)
public class AllTests {}
