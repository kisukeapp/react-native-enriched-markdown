import { normalizeMarkdownStyle } from '../src/normalizeMarkdownStyle';

describe('inline code style', () => {
  it('normalizes cloned-fragment geometry with platform defaults', () => {
    const normalized = normalizeMarkdownStyle({});

    expect(normalized.code).toMatchObject({
      borderRadius: 3,
      paddingHorizontal: 4,
      paddingVertical: 1,
    });
  });

  it('preserves caller geometry for the native renderer', () => {
    const normalized = normalizeMarkdownStyle({
      code: {
        borderRadius: 7,
        paddingHorizontal: 6,
        paddingVertical: 2,
      },
    });

    expect(normalized.code).toMatchObject({
      borderRadius: 7,
      paddingHorizontal: 6,
      paddingVertical: 2,
    });
  });
});
