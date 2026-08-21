#import "CodeBackground.h"
#import "ENRMUIKit.h"
#import "RenderContext.h"

NSString *const CodeAttributeName = @"Code";

static const CGFloat kCodeBackgroundBorderWidth = 0.5;

@implementation CodeBackground {
  StyleConfig *_config;
}

- (instancetype)initWithConfig:(StyleConfig *)config
{
  self = [super init];
  if (self) {
    _config = config;
  }
  return self;
}

- (void)drawBackgroundsForGlyphRange:(NSRange)glyphsToShow
                       layoutManager:(NSLayoutManager *)layoutManager
                       textContainer:(NSTextContainer *)textContainer
                             atPoint:(CGPoint)origin
{
  RCTUIColor *backgroundColor = _config.codeBackgroundColor;
  if (!backgroundColor)
    return;

  NSTextStorage *textStorage = layoutManager.textStorage;
  NSRange charRange = [layoutManager characterRangeForGlyphRange:glyphsToShow actualGlyphRange:NULL];
  if (charRange.location == NSNotFound || charRange.length == 0)
    return;

  [textStorage enumerateAttribute:CodeAttributeName
                          inRange:NSMakeRange(0, textStorage.length)
                          options:0
                       usingBlock:^(id value, NSRange range, BOOL *stop) {
                         if (!value || range.length == 0)
                           return;
                         if (NSIntersectionRange(range, charRange).length == 0)
                           return;

                         [self drawCodeBackgroundForRange:range
                                            layoutManager:layoutManager
                                            textContainer:textContainer
                                                  atPoint:origin
                                          backgroundColor:backgroundColor
                                              borderColor:self->_config.codeBorderColor];
                       }];
}

- (void)drawCodeBackgroundForRange:(NSRange)range
                     layoutManager:(NSLayoutManager *)layoutManager
                     textContainer:(NSTextContainer *)textContainer
                           atPoint:(CGPoint)origin
                   backgroundColor:(RCTUIColor *)backgroundColor
                       borderColor:(RCTUIColor *)borderColor
{
  NSRange glyphRange = [layoutManager glyphRangeForCharacterRange:range actualCharacterRange:NULL];
  if (glyphRange.location == NSNotFound || glyphRange.length == 0)
    return;

  [layoutManager enumerateLineFragmentsForGlyphRange:glyphRange
                                          usingBlock:^(CGRect rect, CGRect usedRect, NSTextContainer *tc,
                                                       NSRange lineRange, BOOL *stop) {
                                            NSRange intersect = NSIntersectionRange(lineRange, glyphRange);
                                            if (intersect.length == 0)
                                              return;

                                            // Clone a precise inline box for every wrapped
                                            // fragment instead of filling the rest of the row.
                                            CGRect textRect = [layoutManager boundingRectForGlyphRange:intersect
                                                                                       inTextContainer:textContainer];
                                            CGRect finalRect =
                                                CGRectMake(textRect.origin.x + origin.x, textRect.origin.y + origin.y,
                                                           textRect.size.width, textRect.size.height);
                                            finalRect = CGRectInset(finalRect, -self->_config.codePaddingHorizontal,
                                                                    -self->_config.codePaddingVertical);

                                            [self drawBackgroundAndBorders:finalRect
                                                           backgroundColor:backgroundColor
                                                               borderColor:borderColor];
                                          }];
}

#pragma mark - Drawing Logic

- (void)drawBackgroundAndBorders:(CGRect)rect
                 backgroundColor:(RCTUIColor *)backgroundColor
                     borderColor:(RCTUIColor *)borderColor
{
  UIBezierPath *path = UIBezierPathWithRoundedRect(rect, _config.codeBorderRadius);
  [backgroundColor setFill];
  [path fill];

  if (borderColor) {
    [borderColor setStroke];
    path.lineWidth = kCodeBackgroundBorderWidth;
    BezierPathSetRoundStyle(path);
    [path stroke];
  }
}

@end
