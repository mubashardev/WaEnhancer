// Register GSAP ScrollTrigger
gsap.registerPlugin(ScrollTrigger);

// Ensure GSAP runs after everything is ready
const initAnimations = () => {
    // Hero Animations
    const heroTl = gsap.timeline();

    heroTl.from(".hero-logo", {
        scale: 0.5,
        opacity: 0,
        y: -50,
        duration: 1.5,
        ease: "elastic.out(1, 0.5)"
    })
    .from(".hero h1", {
        y: 80,
        opacity: 0,
        duration: 1.2,
        ease: "expo.out"
    }, "-=1")
    .from(".hero p", {
        y: 30,
        opacity: 0,
        duration: 1,
        ease: "power3.out"
    }, "-=1")
    .from(".hero-btns", {
        y: 20,
        opacity: 0,
        duration: 1,
        ease: "power3.out"
    }, "-=0.8");

    // Parallax effect for blobs
    gsap.to(".blob-1", {
        scrollTrigger: {
            trigger: "body",
            start: "top top",
            end: "bottom bottom",
            scrub: 2
        },
        y: 250,
        x: 150
    });

    // Header scroll effect
    ScrollTrigger.create({
        start: "top -80",
        onUpdate: (self) => {
            const header = document.querySelector("header");
            if (self.direction === 1) { // Scrolling down
                header.style.padding = "0.75rem 5%";
                header.style.background = "rgba(2, 6, 23, 0.95)";
            } else { // Scrolling up
                header.style.padding = "1rem 5%";
                header.style.background = "rgba(2, 6, 23, 0.7)";
            }
        }
    });

    // Force refresh to handle initial scroll position
    ScrollTrigger.refresh();
};

// Initial check to prevent hidden content if JS is slow
window.addEventListener('load', initAnimations);

// Floating Emojis Logic - Much more subtle, drifting pattern
const emojiContainer = document.getElementById('emoji-container');
const emojis = ['🛡️', '🎨', '📦', '🛠️', '💬', '🔐', '✨', '🚀', '📥', '🔄', '📱', '🔔', '🔒', '📎', '📷', '📍', '👤', '🌐', '⚡', '🌈'];

function createEmoji() {
    if(!emojiContainer) return;
    const emojiEl = document.createElement('div');
    emojiEl.className = 'emoji';
    emojiEl.innerText = emojis[Math.floor(Math.random() * emojis.length)];
    
    // Spread them out more evenly
    const posX = Math.random() * 100;
    const posY = Math.random() * 100;
    
    emojiEl.style.left = `${posX}%`;
    emojiEl.style.top = `${posY}%`;
    
    const size = 1.5 + Math.random() * 2.5;
    const duration = 30 + Math.random() * 60; // Very slow
    
    emojiEl.style.fontSize = `${size}rem`;
    emojiContainer.appendChild(emojiEl);
    
    // Smooth, slow drifting motion
    gsap.to(emojiEl, {
        x: (Math.random() - 0.5) * 150,
        y: (Math.random() - 0.5) * 150,
        rotation: (Math.random() - 0.5) * 90,
        duration: duration,
        repeat: -1,
        yoyo: true,
        ease: "sine.inOut"
    });
}

// Create more for a dense but subtle pattern
for (let i = 0; i < 40; i++) {
    createEmoji();
}

// Smooth Scroll for Navigation
document.querySelectorAll('a[href^="#"]').forEach(anchor => {
    anchor.addEventListener('click', function (e) {
        const targetId = this.getAttribute('href');
        if (targetId === '#' || !targetId.startsWith('#')) return;
        
        e.preventDefault();
        const targetElement = document.querySelector(targetId);
        if (targetElement) {
            targetElement.scrollIntoView({
                behavior: 'smooth'
            });
        }
    });
});
