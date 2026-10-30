class FeaturedStickerPack {
  final String title;
  final String packName;
  final String url;
  final String description;
  final String emoji;
  final String category;

  const FeaturedStickerPack({
    required this.title,
    required this.packName,
    required this.url,
    required this.description,
    required this.emoji,
    required this.category,
  });
}

const List<FeaturedStickerPack> kFeaturedStickerPacks = [
  FeaturedStickerPack(
    title: 'GLW Video Vol 10',
    packName: 'GLWvideovol10_by_fStikBot',
    url: 'https://t.me/addstickers/GLWvideovol10_by_fStikBot',
    description: 'Animated & video pop clips and dynamic reactions',
    emoji: '🎬',
    category: 'Video & Animation',
  ),
  FeaturedStickerPack(
    title: 'Meme Man Memesta',
    packName: 'MemeManMemesta',
    url: 'https://t.me/addstickers/MemeManMemesta',
    description: 'Classic surreal 3D stonks & iconic meme humor',
    emoji: '🗿',
    category: 'Classic Memes',
  ),
  FeaturedStickerPack(
    title: 'Trending Memes 995',
    packName: 'Memess995',
    url: 'https://t.me/addstickers/Memess995',
    description: 'Top viral reaction memes, jokes & daily humor',
    emoji: '😂',
    category: 'Viral Humor',
  ),
  FeaturedStickerPack(
    title: 'TikTok Cats & Animals',
    packName: 'TikTok_cats_animals',
    url: 'https://t.me/addstickers/TikTok_cats_animals',
    description: 'Adorable cats, dogs & viral animal moments',
    emoji: '🐱',
    category: 'Cute Animals',
  ),
];
