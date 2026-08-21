#import "ENRMTailFadeInAnimator.h"
#import "LinkTapUtils.h"
#import <QuartzCore/QuartzCore.h>
#include <TargetConditionals.h>

static const NSTimeInterval kFadeDuration = 0.20;
static const NSTimeInterval kSegmentDelay = 0.024;
static const NSTimeInterval kAcceleratedSegmentDelay = 0.006;
static const NSTimeInterval kMaximumVisualDelay = 0.80;

@interface ENRMFadeEntry : NSObject
@property (nonatomic) NSRange range;
@property (nonatomic, strong) RCTUIColor *color;
@property (nonatomic) CFTimeInterval startTime;
@end

@implementation ENRMFadeEntry
@end

@implementation ENRMTailFadeInAnimator {
  __weak ENRMPlatformTextView *_textView;
#if !TARGET_OS_OSX
  CADisplayLink *_displayLink;
#endif
  NSMutableArray<ENRMFadeEntry *> *_entries;
  CFTimeInterval _nextSegmentStartTime;
}

- (instancetype)initWithTextView:(ENRMPlatformTextView *)textView
{
  self = [super init];
  if (self) {
    _textView = textView;
    _entries = [NSMutableArray array];
  }
  return self;
}

- (void)dealloc
{
#if !TARGET_OS_OSX
  [_displayLink invalidate];
#endif
}

- (NSArray<NSValue *> *)wordSegmentsInString:(NSString *)text range:(NSRange)range
{
  NSMutableArray<NSValue *> *wordRanges = [NSMutableArray array];
  [text enumerateSubstringsInRange:range
                           options:NSStringEnumerationByWords | NSStringEnumerationSubstringNotRequired
                        usingBlock:^(__unused NSString *substring, NSRange substringRange,
                                     __unused NSRange enclosingRange, __unused BOOL *stop) {
                          [wordRanges addObject:[NSValue valueWithRange:substringRange]];
                        }];
  if (wordRanges.count == 0) {
    return @[ [NSValue valueWithRange:range] ];
  }

  NSMutableArray<NSValue *> *segments = [NSMutableArray arrayWithCapacity:wordRanges.count];
  for (NSUInteger index = 0; index < wordRanges.count; index++) {
    NSUInteger start = index == 0 ? range.location : wordRanges[index].rangeValue.location;
    NSUInteger end = index + 1 < wordRanges.count ? wordRanges[index + 1].rangeValue.location : NSMaxRange(range);
    [segments addObject:[NSValue valueWithRange:NSMakeRange(start, end - start)]];
  }
  return segments;
}

- (void)animateFrom:(NSUInteger)tailStart to:(NSUInteger)tailEnd
{
  NSTextStorage *storage = _textView.textStorage;
  if (!storage)
    return;

  CFTimeInterval now = CACurrentMediaTime();
  [self applyEntriesAtTime:now storage:storage];
  if (tailEnd <= tailStart || tailEnd > storage.length)
    return;

#if !TARGET_OS_OSX
  if (UIAccessibilityIsReduceMotionEnabled()) {
    return;
  }
#endif

  NSString *text = storage.string;
  for (NSValue *value in [self wordSegmentsInString:text range:NSMakeRange(tailStart, tailEnd - tailStart)]) {
    NSRange segmentRange = value.rangeValue;
    CFTimeInterval segmentStart = MAX(_nextSegmentStartTime, now);
    CFTimeInterval delay = MAX(segmentStart - now, 0.0);
    _nextSegmentStartTime = segmentStart + (delay < kMaximumVisualDelay ? kSegmentDelay : kAcceleratedSegmentDelay);

    [storage enumerateAttribute:NSForegroundColorAttributeName
                        inRange:segmentRange
                        options:0
                     usingBlock:^(RCTUIColor *color, NSRange colorRange, __unused BOOL *stop) {
                       ENRMFadeEntry *entry = [ENRMFadeEntry new];
                       entry.range = colorRange;
                       entry.color = color ?: [RCTUIColor labelColor];
                       entry.startTime = segmentStart;
                       [self->_entries addObject:entry];
                     }];
  }

  [self applyEntriesAtTime:now storage:storage];

#if !TARGET_OS_OSX
  if (!_displayLink) {
    _displayLink = [CADisplayLink displayLinkWithTarget:self selector:@selector(step:)];
    _displayLink.preferredFramesPerSecond = 0;
    [_displayLink addToRunLoop:[NSRunLoop mainRunLoop] forMode:NSRunLoopCommonModes];
  }
#else
  [self cancel];
#endif
}

#if !TARGET_OS_OSX
- (void)step:(CADisplayLink *)link
{
  NSTextStorage *storage = _textView.textStorage;
  if (!storage)
    return;
  [self applyEntriesAtTime:CACurrentMediaTime() storage:storage];
  if (_entries.count == 0) {
    [_displayLink invalidate];
    _displayLink = nil;
  }
}
#endif

- (void)applyEntriesAtTime:(CFTimeInterval)now storage:(NSTextStorage *)storage
{
  if (_entries.count == 0)
    return;

  NSMutableArray<ENRMFadeEntry *> *completed = [NSMutableArray array];
  [storage beginEditing];
  for (ENRMFadeEntry *entry in _entries) {
    if (NSMaxRange(entry.range) > storage.length) {
      [completed addObject:entry];
      continue;
    }
    CGFloat progress = fmin(MAX((now - entry.startTime) / kFadeDuration, 0.0), 1.0);
    CGFloat eased = 1.0 - (1.0 - progress) * (1.0 - progress);
    [storage addAttribute:NSForegroundColorAttributeName
                    value:[entry.color colorWithAlphaComponent:eased]
                    range:entry.range];
    if (progress >= 1.0) {
      [completed addObject:entry];
    }
  }
  [storage endEditing];
  [_entries removeObjectsInArray:completed];
}

- (void)cancel
{
#if !TARGET_OS_OSX
  [_displayLink invalidate];
  _displayLink = nil;
#endif
  NSTextStorage *storage = _textView.textStorage;
  if (storage) {
    [storage beginEditing];
    for (ENRMFadeEntry *entry in _entries) {
      if (NSMaxRange(entry.range) <= storage.length) {
        [storage addAttribute:NSForegroundColorAttributeName value:entry.color range:entry.range];
      }
    }
    [storage endEditing];
  }
  [_entries removeAllObjects];
  _nextSegmentStartTime = 0;
}

@end
