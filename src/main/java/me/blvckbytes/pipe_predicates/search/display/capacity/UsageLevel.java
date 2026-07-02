package me.blvckbytes.pipe_predicates.search.display.capacity;

public enum UsageLevel {
  NO_USAGE,
  MODERATE_USAGE,
  MEDIUM_USAGE,
  HIGH_USAGE,
  FULL_USAGE,
  ;

  public static UsageLevel fromUsagePercentage(double usagePercentage) {
    if (usagePercentage == 0)
      return NO_USAGE;

    if (usagePercentage < 33)
      return MODERATE_USAGE;

    if (usagePercentage < 66)
      return MEDIUM_USAGE;

    if (usagePercentage < 99)
      return HIGH_USAGE;

    return FULL_USAGE;
  }
}
