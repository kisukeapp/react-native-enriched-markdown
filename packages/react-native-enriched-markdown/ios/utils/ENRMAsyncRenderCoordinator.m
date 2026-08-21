#import "ENRMAsyncRenderCoordinator.h"

@interface ENRMAsyncRenderCoordinator ()
- (void)drainLatestRender;
@end

@implementation ENRMAsyncRenderCoordinator {
  dispatch_queue_t _queue;
  NSUInteger _currentRenderId;
  BOOL _workerActive;
  BOOL (^_pendingRender)(void);
  dispatch_block_t _pendingApply;
  NSUInteger _pendingRenderId;
}

- (instancetype)initWithQueueLabel:(const char *)label
{
  if (self = [super init]) {
    _queue = dispatch_queue_create(label, DISPATCH_QUEUE_SERIAL);
  }
  return self;
}

- (void)scheduleRender:(BOOL (^)(void))renderBlock apply:(dispatch_block_t)applyBlock
{
  if (_blockAsyncRender)
    return;

  BOOL shouldStartWorker = NO;
  @synchronized(self) {
    _currentRenderId += 1;
    _pendingRenderId = _currentRenderId;
    _pendingRender = [renderBlock copy];
    _pendingApply = [applyBlock copy];
    if (!_workerActive) {
      _workerActive = YES;
      shouldStartWorker = YES;
    }
  }
  if (shouldStartWorker) {
    dispatch_async(_queue, ^{ [self drainLatestRender]; });
  }
}

- (void)drainLatestRender
{
  while (YES) {
    __block BOOL (^renderBlock)(void) = nil;
    __block dispatch_block_t applyBlock = nil;
    __block NSUInteger renderId = 0;
    @synchronized(self) {
      renderBlock = _pendingRender;
      applyBlock = _pendingApply;
      renderId = _pendingRenderId;
      _pendingRender = nil;
      _pendingApply = nil;
      if (!renderBlock) {
        _workerActive = NO;
        return;
      }
    }

    if (!renderBlock())
      continue;
    dispatch_async(dispatch_get_main_queue(), ^{
      BOOL isCurrent;
      @synchronized(self) {
        isCurrent = renderId == self->_currentRenderId;
      }
      if (isCurrent) {
        applyBlock();
      }
    });
  }
}

- (void)invalidate
{
  @synchronized(self) {
    _currentRenderId += 1;
    _pendingRender = nil;
    _pendingApply = nil;
  }
}

@end
